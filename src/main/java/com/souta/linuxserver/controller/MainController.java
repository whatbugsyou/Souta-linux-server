package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.entity.DeadLine;
import com.souta.linuxserver.entity.Line;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.Socks5Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

import static com.souta.linuxserver.controller.Host.id;
import static com.souta.linuxserver.controller.Host.java_server_host;
import static com.souta.linuxserver.service.LineService.dialingLines;
import static com.souta.linuxserver.service.LineService.lineRedialWait;

@RestController
@RequestMapping("/v1.0/line/notify")
public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final int checkingTimesOfDefineDeadLine = 3;
    /**
     * record the times of false dial ,if the times are >= checkingTimesOfDefineDeadLine will send to java server as a dead line
     */
    private static final ConcurrentHashMap<String, Integer> dialFalseTimesMap = new ConcurrentHashMap<>();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    /**
     * the line that is error sent is going to be resent.
     */
    private static final Set<Line> errorSendLines = new HashSet<>();
    private final PPPOEService pppoeService;
    private final ShadowsocksService shadowsocksService;
    private final Socks5Service socks5Service;
    private final LineService lineService;

    public MainController(PPPOEService pppoeService, ShadowsocksService shadowsocksService, Socks5Service socks5Service, LineService lineService) {
        this.pppoeService = pppoeService;
        this.shadowsocksService = shadowsocksService;
        this.socks5Service = socks5Service;
        this.lineService = lineService;
    }

    @PostConstruct
    public void init() {
        new Host().init();
        initLines();
        monitorLines();
    }

    /**
     * monitor deadline : redial false with 3 times will be defined as a deadline .it is recorded in redialCheckMap.check redialCheckMap for deadline and send it to java server.
     * monitor fullDial : if dial-upped lines count is less than the count of server configured ,it will call addOneDial to make full use of server line resources.
     * monitor errorLine: if error occurs in sending  line info,it will resend when  errorSendLines is checked once a period of 30s.
     */
    private void monitorLines() {
        log.info("monitorLines starting...");
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(3);
        Runnable checkFullDial = () -> {
            String lineID = lineService.generateLineID();
            if (lineID != null) {
                boolean addTrue = dialingLines.add(lineID);
                if (addTrue) {
                    executorService.execute(() -> {
                        log.info("LineMonitor is going to create line{} after {} seconds...", lineID, lineRedialWait);
                        try {
                            TimeUnit.SECONDS.sleep(lineRedialWait);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        createLine(lineID);
                    });
                }
            }
        };
        Runnable checkDeadLine = () -> {
            Set<Map.Entry<String, Integer>> entries = dialFalseTimesMap.entrySet();
            for (Map.Entry<String, Integer> entry : entries
            ) {
                Integer value = entry.getValue();
                if (value == checkingTimesOfDefineDeadLine) {
                    HashMap<String, Object> data = new HashMap<>();
                    DeadLine deadLine = new DeadLine();
                    deadLine.setLineId(entry.getKey());
                    ADSL adsl = pppoeService.getADSLList().get(Integer.parseInt(entry.getKey()) - 1);
                    deadLine.setAdslUser(adsl.getAdslUser());
                    deadLine.setAdslPassword(adsl.getAdslPassword());
                    data.put("hostId", id);
                    data.put("deadLine", deadLine);
                    String body = new JSONObject(data).toJSONString();
                    log.info("send deadLine Info :");
                    log.info(body);
                    Runnable runnable = () -> {
                        try {
                            int status = HttpRequest.post(java_server_host + "/v1.0/deadLine")
                                    .body(body)
                                    .execute().getStatus();
                            if (status != 200) {
                                throw new ResponseNotOkException("error in sending dead line info to the java server,API(POST) :  /v1.0/deadLine");
                            }
                        }catch (Exception e){
                            log.error(e.getMessage());
                        }
                    };
                    executorService.execute(runnable);
                    dialFalseTimesMap.put(entry.getKey(), value + 1);
                }
            }
        };

        Runnable checkErrorSendLines = () -> {
            if (!errorSendLines.isEmpty()) {
                sendLinesInfo(new ArrayList<>(errorSendLines));
            }
        };
        scheduler.scheduleAtFixedRate(checkErrorSendLines, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(checkFullDial, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(checkDeadLine, 0, 30, TimeUnit.SECONDS);
    }

    /**
     * scan all lines and filter lines started socks as ok lines,delete not ok lines.And then ,send the ok lines to the Java server.
     */
    public void initLines() {
        log.info("initLineInfo....");
        HashSet<String> lineIdSet = pppoeService.getDialuppedIdSet();
        ArrayList<Line> lines = (ArrayList<Line>) lineService.getLines(lineIdSet);
        log.info("total {} lines is ok", lines.size());
        //        clean();
        sendLinesInfo(lines);
    }

    @GetMapping("/all")
    public void checkAndSendAllLinesInfo() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                log.info("check all line....");
                HashSet<String> lineIdSet = pppoeService.getDialuppedIdSet();
                ArrayList<Line> lines = (ArrayList<Line>) lineService.getLines(lineIdSet);
                log.info("total {} lines is ok", lines.size());
                //        clean();
                sendLinesInfo(lines);
            }
        });
    }

    @DeleteMapping("/all")
    public void clean() {
        log.info("clean all Line in Java Server");
        try {
            int status = HttpRequest.delete(java_server_host + "/v1.0/server/lines?" + "hostId=" + id)
                    .execute().getStatus();
            if (status != 200) {
                throw new ResponseNotOkException("error in deleting All Line from java server,API(DELETE) :  /v1.0/server/lines ");
            }
        }catch (Exception e){
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
        if (lineService.checkExits(lineId)) {
            resultMap.put("status", "exist");
        } else {
            resultMap.put("status", "ok");
        }
        executorService.execute(() -> {
            FutureTask<Line> futureTask = lineService.createLine(lineId);
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
        executorService.execute(() -> {
            FutureTask<Line> futureTask = lineService.refreshLine(lineId);
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
            Integer integer = dialFalseTimesMap.get(lineId);
            if (integer != null) {
                dialFalseTimesMap.put(lineId, integer + 1);
            } else {
                dialFalseTimesMap.put(lineId, 1);
            }
        } else {
            sendLineInfo(line);
            dialFalseTimesMap.remove(lineId);
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

    @DeleteMapping()
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
        if (lineService.editProtoInLine(lineId, protoId, action)) {
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
            data.put("hostId", id);
            data.put("lines", lines);
            String body = new JSONObject(data).toJSONString();
            log.info("send Lines Info ...");
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean status = HttpRequest.put(java_server_host + "/v1.0/line")
                                .body(body)
                                .execute().isOk();
                        if (status) {
                            errorSendLines.removeAll(lines);
                        } else {
                            throw new ResponseNotOkException("response not OK in sendLinesInfo to the Java server,API(PUT) :  /v1.0/line ");
                        }
                    } catch (RuntimeException | ResponseNotOkException e) {
                        log.error(e.getMessage());
                        errorSendLines.addAll(lines);
                    }
                    log.info(body);
                }
            };
            executorService.execute(runnable);
        }
    }

    private void sendLineInfo(Line line) {
        ArrayList<Line> list = new ArrayList<>();
        list.add(line);
        sendLinesInfo(list);
    }

}
