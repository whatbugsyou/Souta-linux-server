package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.DeadLine;
import com.souta.linuxserver.entity.Line;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.souta.linuxserver.monitor.LineMonitor.*;
import static com.souta.linuxserver.service.impl.LineServiceImpl.DEFAULT_LISTEN_IP;

@RestController
@RequestMapping("/v1.0/line/notify")
public class LineController {
    private static final Logger log = LoggerFactory.getLogger(LineController.class);
    private static final int checkingTimesOfDefineDeadLine = 3;
    /**
     * record the times of false dial ,if the times are >= checkingTimesOfDefineDeadLine will send to java server as a dead line
     * lineId:dialFalseTimes
     */
    private static final ConcurrentHashMap<String, Integer> dialFalseTimesMap = new ConcurrentHashMap<>();

    /**
     * the line that is error sent is going to be resent.
     */
    private static final Set<Line> errorSendLines = new HashSet<>();
    private static final Set<String> deadLineToSend = new HashSet<>();
    private final PPPOEService pppoeService;
    private final ShadowsocksService shadowsocksService;
    private final Socks5Service socks5Service;
    private final LineService lineService;
    private final HostConfig hostConfig;
    private final RateLimitService rateLimitService;
    private final CommandService commandService;
    @Autowired
    @Qualifier("refreshPool")
    private ExecutorService refreshPool;

    @Autowired
    @Qualifier("netPool")
    private ExecutorService netPool;

    @Autowired
    @Qualifier("basePool")
    private ExecutorService basePool;

    public LineController(PPPOEService pppoeService, ShadowsocksService shadowsocksService, @Qualifier("v2raySocks5ServiceImpl") Socks5Service socks5Service, LineService lineService, HostConfig hostConfig, RateLimitService rateLimitService, CommandService commandService) {
        this.pppoeService = pppoeService;
        this.shadowsocksService = shadowsocksService;
        this.socks5Service = socks5Service;
        this.lineService = lineService;
        this.hostConfig = hostConfig;
        this.rateLimitService = rateLimitService;
        this.commandService = commandService;
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
        ArrayList<Line> lines = (ArrayList<Line>) lineService.getLinesWithDefaultListenIP(lineIdSet);
        log.info("total {} lines is ok", lines.size());
        sendLinesInfo(lines);
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

    @PostMapping
    public HashMap<String, Object> createLine() {
        String lineId = lineService.generateLineID();
        if (lineId == null) {
            HashMap<String, Object> resultMap = new HashMap<>();
            String message = "error GenerateLineID, adsl account is used up";
            log.info("create Line ,{}", message);
            resultMap.put("status", message);
            return resultMap;
        } else {
            log.info("create Line {}", lineId);
            return createLine(lineId);
        }
    }

    private HashMap<String, Object> createLine(String lineId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        if (lineService.checkExitsWithDefaultListenIP(lineId)) {
            resultMap.put("status", "exist");
        } else {
            resultMap.put("status", "ok");
        }
        refreshPool.submit(() -> {
            FutureTask<Line> futureTask = lineService.createLineWithDefaultListenIP(lineId);
            lineReturnHandle(lineId, futureTask);
        });
        return resultMap;
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
            FutureTask<Line> futureTask = lineService.refreshLineWithDefaultListenIP(lineId);
            lineReturnHandle(lineId, futureTask);
        });
        return resultMap;
    }

    /**
     * if line what futureTask gets is not null,it will send the line to java server,otherwise it will record the line ID in redialCheckMap.
     *
     * @param lineId
     * @param futureTask
     */
    private void lineReturnHandle(String lineId, FutureTask<Line> futureTask) {
        Line line = null;
        try {
            if (futureTask != null) {
                line = futureTask.get();
            } else {
                return;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        if (line == null) {
            dialFalseTimesMap.merge(lineId, 1, Integer::sum);
            if (dialFalseTimesMap.get(lineId) == checkingTimesOfDefineDeadLine) {
                deadLineIdSet.add(lineId);
                deadLineToSend.add(lineId);
            }
        } else {
            sendLineInfo(line);
            dialFalseTimesMap.remove(lineId);
            deadLineIdSet.remove(lineId);
            deadLineToSend.remove(lineId);
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
        if (lineService.editProtoInLineWithDefaultListenIP(lineId, protoId, action)) {
            resultMap.put("status", "ok");
        } else {
            resultMap.put("status", "not exist");
        }
        return resultMap;
    }

    /**
     * send lines to the Java server. If response status is not OK or catch an exception, will add lines into errorSendLines, ready checking thread to invoke resend.
     *
     * @param lines
     */
    private void sendLinesInfo(ArrayList<Line> lines) {
        if (!lines.isEmpty()) {
            HashMap<String, Object> data = new HashMap<>();
            data.put("hostId", hostConfig.getHost().getId());
            data.put("lines", lines);
            String body = new JSONObject(data).toJSONString();
            Runnable runnable = () -> {
                log.info("send Lines Info ...");
                try (HttpResponse response = HttpRequest.put(hostConfig.getJavaServerHost() + "/v1.0/line")
                        .body(body)
                        .timeout(5000)
                        .execute()) {
                    boolean status = response.isOk();
                    if (status) {
                        log.info("send Lines Info ok : {}", body);
                        lines.forEach(errorSendLines::remove);
                    } else {
                        throw new ResponseNotOkException("response not OK in sendLinesInfo to the Java server, API(PUT): /v1.0/line, " + response.body());
                    }
                } catch (RuntimeException | ResponseNotOkException e) {
                    log.error(e.getMessage());
                    log.info("send Lines Info NOT ok : {}", body);
                    lines.forEach(errorSendLines::remove);
                    errorSendLines.addAll(lines);
                }
            };
            netPool.submit(runnable);
        }
    }

    private void sendLineInfo(Line line) {
        ArrayList<Line> list = new ArrayList<>();
        list.add(line);
        sendLinesInfo(list);
    }
}
