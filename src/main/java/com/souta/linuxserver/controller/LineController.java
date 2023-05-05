package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.line.Line;
import com.souta.linuxserver.line.LineSender;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.Socks5Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

import static com.souta.linuxserver.monitor.LineMonitor.*;
import static com.souta.linuxserver.service.impl.LineServiceImpl.DEFAULT_LISTEN_IP;

@RestController
@RequestMapping("/v1.0/line/notify")
public class LineController {
    private static final Logger log = LoggerFactory.getLogger(LineController.class);
    private final PPPOEService pppoeService;
    private final ShadowsocksService shadowsocksService;
    private final Socks5Service socks5Service;
    private final LineService lineService;
    private final HostConfig hostConfig;
    private final LineSender lineSender;

    @Autowired
    @Qualifier("refreshPool")
    private ExecutorService refreshPool;

    @Autowired
    @Qualifier("basePool")
    private ExecutorService basePool;

    public LineController(PPPOEService pppoeService, ShadowsocksService shadowsocksService, @Qualifier("v2raySocks5ServiceImpl") Socks5Service socks5Service, LineService lineService, HostConfig hostConfig, LineSender lineSender) {
        this.pppoeService = pppoeService;
        this.shadowsocksService = shadowsocksService;
        this.socks5Service = socks5Service;
        this.lineService = lineService;
        this.hostConfig = hostConfig;
        this.lineSender = lineSender;
    }


    @PostConstruct
    public void init() {
        checkAndSendAllLinesInfo();
    }

    @GetMapping("/all")
    public void getAllLines() {
        basePool.execute(this::checkAndSendAllLinesInfo);
    }

    /**
     * scan all lines and filter lines started socks as ok lines,delete not ok lines.And then ,send the ok lines to the Java server.
     */
    public void checkAndSendAllLinesInfo() {
        log.info("check all line....");
        HashSet<String> lineIdSet = pppoeService.getDialuppedIdSet();
        ArrayList<Line> lines = (ArrayList<Line>) lineService.getLines(lineIdSet);
        log.info("total {} lines is ok", lines.size());
        lineSender.sendLinesInfo(lines);
    }

    @DeleteMapping("/all")
    public void clean() {
        log.info("clean all Line in Java Server");
        try (HttpResponse response = HttpRequest.delete(hostConfig.getJavaServerHost() + "/v1.0/server/lines?" + "hostId=" + hostConfig.getHost().getId())
                .execute()) {
            int status = response.getStatus();
            if (status != 200) {
                throw new ResponseNotOkException("error in deleting All Line from java server,API(DELETE) :  /v1.0/server/lines ");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }


    @PutMapping
    public HashMap<String, Object> refreshLine(String lineId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        log.info("refresh Line {}", lineId);
        if (!pppoeService.isDialUp(lineId)) {
            resultMap.put("status", "not exist");
        } else {
            resultMap.put("status", "ok");
        }
        refreshPool.execute(() -> {
            dialFalseTimesMap.remove(lineId);
            deadLineIdSet.remove(lineId);
            Line line = lineService.refresh(lineId);
            lineReturnHandle(line);
        });
        return resultMap;
    }

    /**
     * if line what futureTask gets is not null,it will send the line to java server,otherwise it will record the line ID in redialCheckMap.
     */
    private void lineReturnHandle(Line line) {
        String lineId = line.getLineId();
        if (line != null) {
            if (line.getOutIpAddr() != null) {
                lineSender.sendLineInfo(line);
                dialFalseTimesMap.remove(lineId);
                deadLineIdSet.remove(lineId);
                deadLineToSend.remove(lineId);
            } else {
                dialFalseTimesMap.merge(lineId, 1, Integer::sum);
                if (dialFalseTimesMap.get(lineId) == checkingTimesOfDefineDeadLine) {
                    deadLineIdSet.add(lineId);
                    deadLineToSend.add(line);
                }
            }
        }
    }

    @GetMapping
    public HashMap<String, Object> checkLine(String lineId) {
        log.info("check Line {}", lineId);
        HashMap<String, Object> resultMap = new HashMap<>();
        boolean exist = pppoeService.isDialUp(lineId);
        if (!exist) {
            resultMap.put("status", "not exist");
            resultMap.put("data", null);
        } else {
            resultMap.put("status", "ok");
            HashMap<Object, Object> data = new HashMap<>();
            boolean start = socks5Service.isStart(lineId, DEFAULT_LISTEN_IP);
            if (start) {
                data.put("socks5", "on");
            } else {
                data.put("socks5", "off");
            }
            boolean start1 = shadowsocksService.isStart(lineId, DEFAULT_LISTEN_IP);
            if (start1) {
                data.put("shadowsocks", "on");
            } else {
                data.put("shadowsocks", "off");
            }
            resultMap.put("data", data);
        }
        return resultMap;
    }

    @DeleteMapping()
    public HashMap<String, Object> deleteLine(String lineId) {
        log.info("delete line {}, and do not dialing automatically", lineId);
        deadLineIdSet.add(lineId);
        HashMap<String, Object> resultMap = new HashMap<>();
        basePool.execute(() -> lineService.deleteLine(lineId));
        resultMap.put("status", "ok");
        return resultMap;
    }

    @GetMapping("/proto")
    public HashMap<String, Object> proto(String lineId, String protoId, String action) {
        HashMap<String, Object> resultMap = new HashMap<>();
        log.info("proto change : line {} , {} ,{}", lineId, protoId, action);
        if (lineService.editProtoInLine(lineId, protoId, action)) {
            resultMap.put("status", "ok");
        } else {
            resultMap.put("status", "not exist");
        }
        return resultMap;
    }

}
