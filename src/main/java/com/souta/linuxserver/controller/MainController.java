package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.DeadLine;
import com.souta.linuxserver.entity.Line;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.Socks5Service;
import com.souta.linuxserver.util.LineMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/v1.0/line/notify")
public class MainController {
    private final PPPOEService pppoeService;
    private final ShadowsocksService shadowsocksService;
    private final Socks5Service socks5Service;
    private final LineService lineService;
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final int checkingTimesOfDefineDeadLine = 3;

    private static final HashMap<String, Integer> redialCheckMap = new HashMap<>();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Set<Line> errorSendLines = new HashSet<>();
    public MainController(PPPOEService pppoeService, ShadowsocksService shadowsocksService, Socks5Service socks5Service, LineService lineService) {
        this.pppoeService = pppoeService;
        this.shadowsocksService = shadowsocksService;
        this.socks5Service = socks5Service;
        this.lineService = lineService;
    }

    @PostConstruct
    public void init() {
        new Host().init();
        initSendLineInfo();
        monitorLines();
    }

    private void monitorLines() {
        Runnable addOneDial = () -> {
            String lineID = GenerateLineID();
            if (lineID != null) {
                LineService.dialingLines.add(lineID);
                log.info("LineMonitor is going to create line{} after {} seconds...", lineID, lineService.lineRedialWait);
                try {
                    TimeUnit.SECONDS.sleep(lineService.lineRedialWait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                createLine(lineID);
            }
        };
        log.info("monitorLines starting...");
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(3);
        Runnable checkFullDial = () -> {
            HashSet<String> newlineIdList = pppoeService.getDialuppedIdSet();
            newlineIdList.addAll(LineService.dialingLines);
            if (newlineIdList.size() < pppoeService.getADSLList().size()) {
                executorService.execute(addOneDial);
            }
        };
        Runnable checkDeadLine = () -> {
            Set<Map.Entry<String, Integer>> entries = redialCheckMap.entrySet();
            for (Map.Entry<String, Integer> entry : entries
            ) {
                Integer value = entry.getValue();
                if (value == checkingTimesOfDefineDeadLine) {
                    HashMap<String, Object> data = new HashMap<>();
                    DeadLine deadLine = new DeadLine();
                    deadLine.setLineId(entry.getKey());
                    ADSL adsl = pppoeService.getADSLList().get(Integer.parseInt(entry.getKey()));
                    deadLine.setAdslUser(adsl.getAdslUser());
                    deadLine.setAdslPassword(adsl.getAdslPassword());
                    data.put("hostId", Host.id);
                    data.put("deadLine", deadLine);
                    String body = new JSONObject(data).toJSONString();
                    log.info("send deadLine Info :");
                    log.info(body);
                    Runnable runnable = () -> {
                        int status = HttpRequest.post(Host.java_server_host + "/v1.0/deadLine")
                                .body(body)
                                .execute().getStatus();
                        if (status != 200) {
                            log.error("error in send dead line info to java server,API(POST) :  /v1.0/deadLine");
                        }
                    };
                    executorService.execute(runnable);
                    redialCheckMap.put(entry.getKey(), value + 1);
                }
            }
        };

        Runnable checkErrorSendLines = new Runnable() {
            @Override
            public void run() {
                if (!errorSendLines.isEmpty()) {
                    sendLinesInfo(new ArrayList<>(errorSendLines));
                }
            }
        };
        scheduler.scheduleAtFixedRate(checkErrorSendLines, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(checkFullDial, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(checkDeadLine, 0, 30, TimeUnit.SECONDS);
    }

    private void initSendLineInfo() {
        log.info("initLineInfo....");
        HashSet<String> lineIdSet = pppoeService.getDialuppedIdSet();
        ArrayList<Line> lines = getLines(lineIdSet);
        log.info("total {} lines is ok", lines.size());
        int status = HttpRequest.delete(Host.java_server_host + "/v1.0/server/lines?" + "hostId=" + Host.id)
                .execute().getStatus();
        if (status != 200) {
            log.error("error in delete All Line from java server,API(DELETE) :  /v1.0/server/lines ");
        }
        sendLinesInfo(lines);
    }

    @PostMapping
    public HashMap<String, Object> createLine() {
        String lineId = GenerateLineID();
        if (lineId == null) {
            HashMap<String, Object> resultMap = new HashMap<>();
            resultMap.put("status", "error GenerateLineID ,  adsl acount is used up");
            return resultMap;
        } else {
            log.info("create Line {}", lineId);
            return createLine(lineId);
        }
    }

    public HashMap<String, Object> createLine(String lineId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        if (lineService.checkExits(lineId)){
            resultMap.put("status", "exist");
        } else {
            resultMap.put("status", "ok");
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                FutureTask<Line> futureTask = lineService.createLine(lineId);
                lineReturnHandle(lineId,futureTask);
            }
        });
        return resultMap;
    }
    @PutMapping
    public HashMap<String, Object> refreshLine(String lineId) {
        HashMap<String, Object> resultMap = new HashMap<>();
            log.info("refresh Line {}", lineId);
        if (!pppoeService.isDialUp(lineId)) {
            resultMap.put("status", "not exist");
        }else {
            resultMap.put("status", "ok");
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                FutureTask<Line> futureTask = lineService.refreshLine(lineId);
                lineReturnHandle(lineId,futureTask);
            }
        });
        return resultMap;
    }

    private void lineReturnHandle(String lineId, FutureTask<Line> futureTask) {
        Runnable LineReturnHandle = () -> {
            Line line = null;
            try {
                line = futureTask.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            if (line == null) {
                Integer integer = redialCheckMap.get(lineId);
                if (integer != null) {
                    redialCheckMap.put(lineId, integer + 1);
                } else {
                    redialCheckMap.put(lineId, 1);
                }
            }else {
                sendLinesInfo(line);
                redialCheckMap.remove(lineId);
            }
        };
        executorService.execute(LineReturnHandle);
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
            boolean start = socks5Service.isStart(lineId);
            if (start) {
                data.put("socks5", "on");
            } else {
                data.put("socks5", "off");
            }
            boolean start1 = shadowsocksService.isStart(lineId);
            if (start1) {
                data.put("shadowsocks", "on");
            } else {
                data.put("shadowsocks", "off");
            }
            resultMap.put("data", data);
        }
        return resultMap;
    }

    @DeleteMapping
    public HashMap<String, Object> deleteLine(String lineId) {
        log.info("delete line {}", lineId);
        HashMap<String, Object> resultMap = new HashMap<>();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                lineService.deleteLine(lineId);
            }
        });
        resultMap.put("status", "ok");
        return resultMap;
    }

    @GetMapping("/proto")
    public HashMap<String, Object> proto(String lineId, String protoId, String action) {
        HashMap<String, Object> resultMap = new HashMap<>();
            log.info("proto change : line {} , {} ,{}", lineId, protoId, action);
            if (lineService.editProtoInLine(lineId,protoId,action)) {
                resultMap.put("status", "ok");
            }else {
                resultMap.put("status", "not exist");
            }
            return resultMap;
    }

    private void sendLinesInfo(ArrayList<Line> lines) {
        if (!lines.isEmpty()) {
            HashMap<String, Object> data = new HashMap<>();
            data.put("hostId", Host.id);
            data.put("lines", lines);
            String body = new JSONObject(data).toJSONString();
            log.info("send Lines Info ...");
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean status = HttpRequest.put(Host.java_server_host + "/v1.0/line")
                                .body(body)
                                .execute().isOk();
                        if (status) {
                            log.info(body);
                            if (!errorSendLines.isEmpty()) {
                                errorSendLines.removeAll(lines);
                            }
                        } else {
                            log.error("not ok  in sendLinesInfo to java server,API(PUT) :  /v1.0/lines ");
                            errorSendLines.addAll(lines);
                        }
                    } catch (RuntimeException e) {
                        log.error(e.getMessage());
                        log.error("exception in sendLinesInfo to java server,API(PUT) :  /v1.0/lines ");
                        errorSendLines.addAll(lines);
                    }
                }
            };
            executorService.execute(runnable);
        }
    }

    private void sendLinesInfo(Line line) {
        ArrayList<Line> list = new ArrayList<>();
        list.add(line);
        sendLinesInfo(list);
    }

    private ArrayList<Line> getLines(HashSet<String> lineIdList) {
        ArrayList<Line> lines = new ArrayList();
        //sort id (String type)
        TreeSet<Integer> integers = new TreeSet<>();
        for (String id : lineIdList
        ) {
            integers.add(Integer.valueOf(id));
        }
        for (Integer id : integers
        ) {
            String lineId = id.toString();
            Line line = lineService.getLine(lineId);
            if (line!=null){
                log.info("Line {} is ok", lineId);
                lines.add(line);
            } else {
                log.warn("Line {} is not ok", lineId);
                deleteLine(lineId);
            }
        }
        return lines;
    }

    private String GenerateLineID() {
        LineMax lineMax = new LineMax();
        HashSet<String> dialuppedId = pppoeService.getDialuppedIdSet();
        dialuppedId.addAll(lineService.dialingLines);
        List<ADSL> adslList = pppoeService.getADSLList();
        if (dialuppedId.size() < adslList.size()) {
            for (String id :
                    dialuppedId) {
                lineMax.add(Integer.parseInt(id));
            }
            return String.valueOf(lineMax.getMax());
        }
        return null;
    }

}
