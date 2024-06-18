package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.line.Line;
import com.souta.linuxserver.line.LineSender;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import static com.souta.linuxserver.monitor.LineMonitor.deadLineIdSet;

@RestController
@RequestMapping("/v1.0/line/notify")
@Slf4j
public class LineController {
    private final PPPOEService pppoeService;
    private final LineService lineService;
    private final HostConfig hostConfig;
    private final LineSender lineSender;

    @Autowired
    @Qualifier("refreshPool")
    private ExecutorService refreshPool;

    @Autowired
    @Qualifier("basePool")
    private ExecutorService basePool;

    public LineController(PPPOEService pppoeService, LineService lineService, HostConfig hostConfig, LineSender lineSender) {
        this.pppoeService = pppoeService;
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
        ArrayList<Line> lines = (ArrayList<Line>) lineService.getAvailableLines();
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
            Line line = lineService.refresh(lineId);
            lineSender.sendLineInfo(line);
        });
        return resultMap;
    }

    @DeleteMapping()
    public HashMap<String, Object> deleteLine(String lineId) {
        log.info("delete line {}, and do not dialing automatically", lineId);
        // TODO code level change
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
