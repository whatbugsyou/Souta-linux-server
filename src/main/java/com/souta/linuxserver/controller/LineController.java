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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.souta.linuxserver.service.LineService.deadLineIdSet;
import static com.souta.linuxserver.service.LineService.dialingLines;
import static com.souta.linuxserver.service.Socks5Service.onStartingSocks;
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
    private final NamespaceService namespaceService;
    private final HostConfig hostConfig;
    private final RateLimitService rateLimitService;
    @Autowired
    @Qualifier("refreshPool")
    private ExecutorService refreshPool;

    @Autowired
    @Qualifier("netPool")
    private ExecutorService netPool;

    @Autowired
    @Qualifier("basePool")
    private ExecutorService basePool;


    public LineController(PPPOEService pppoeService, ShadowsocksService shadowsocksService, @Qualifier("v2raySocks5ServiceImpl") Socks5Service socks5Service, LineService lineService, NamespaceService namespaceService, HostConfig hostConfig, RateLimitService rateLimitService) {
        this.pppoeService = pppoeService;
        this.shadowsocksService = shadowsocksService;
        this.socks5Service = socks5Service;
        this.lineService = lineService;
        this.namespaceService = namespaceService;
        this.hostConfig = hostConfig;
        this.rateLimitService = rateLimitService;
    }

    @PostConstruct
    public void init() {
        checkAndSendAllLinesInfo();
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
                Executors.newScheduledThreadPool(5);
        // TODO batched create
        Runnable checkFullDial = () -> {
            String lineID = lineService.generateLineID();
            if (lineID != null) {
                boolean addTrue = dialingLines.add(lineID);
                if (addTrue) {
                    basePool.execute(() -> {
                        log.info("LineMonitor is going to create line{}...", lineID);
                        createLine(lineID);
                    });
                }
            }
        };
        Runnable keepCPUHealth = () -> {
            String cmd = "top -b -n 1 |sed -n '8p'|awk '{print $1,$9,$12}'";
            InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                Pattern compile = Pattern.compile("(\\d+) (.+) (.+)");
                try {
                    if ((line = bufferedReader.readLine()) != null) {
                        Matcher matcher = compile.matcher(line);
                        if (matcher.matches()) {
                            String pid = matcher.group(1);
                            Float cpu = Float.valueOf(matcher.group(2));
                            String command = matcher.group(3);
                            if (cpu > 100 && command.contains("ss5")) {
                                log.info("CPUHealthMonitor is going to kill pid{}---{}%...", pid, cpu);
                                namespaceService.exeCmdInDefaultNamespace("kill -9 " + pid);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        };

        Runnable checkFullSocksStart = () -> {
            HashSet<String> socks5ServiceStartedIdSet = socks5Service.getStartedIdSet();
            HashSet<String> dialuppedIdSet = pppoeService.getDialuppedIdSet();
            dialuppedIdSet.removeAll(socks5ServiceStartedIdSet);
            dialuppedIdSet.removeAll(dialingLines);
            dialuppedIdSet.removeAll(onStartingSocks);
            dialuppedIdSet.forEach(lineID -> {
                boolean addTrue = onStartingSocks.add(lineID);
                if (addTrue) {
                    basePool.execute(() -> {
                        try {
                            log.info("SocksMonitor is going to restart socks5-{}...", lineID);
                            socks5Service.restartSocks(lineID, DEFAULT_LISTEN_IP);
                            boolean isStart = false;
                            int testTimes = 0;
                            while (!isStart) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                isStart = socks5Service.isStart(lineID, DEFAULT_LISTEN_IP);
                                testTimes++;
                                if (testTimes == 3) {
                                    break;
                                }
                            }
                            log.info("restart socks5-{} {}", lineID, isStart ? "ok" : "failure");
                        } finally {
                            onStartingSocks.remove(lineID);
                        }
                    });
                }
            });
        };

        Runnable checkDeadLine = () -> {
            if (!deadLineToSend.isEmpty()){
                deadLineToSend.forEach(lineId -> {
                    HashMap<String, Object> data = new HashMap<>();
                    DeadLine deadLine = new DeadLine();
                    deadLine.setLineId(lineId);
                    ADSL adsl = pppoeService.getADSLList().get(Integer.parseInt(lineId) - 1);
                    deadLine.setAdslUser(adsl.getAdslUser());
                    deadLine.setAdslPassword(adsl.getAdslPassword());
                    data.put("hostId", hostConfig.getHost().getId());
                    data.put("deadLine", deadLine);
                    String body = new JSONObject(data).toJSONString();
                    Runnable runnable = () -> {
                        try {
                            log.info("send deadLine Info : {}", body);
                            int status = HttpRequest.post(hostConfig.getJavaServerHost() + "/v1.0/deadLine")
                                    .body(body)
                                    .execute().getStatus();
                            if (status != 200) {
                                throw new ResponseNotOkException("error in sending dead line info to the java server,API(POST) :  /v1.0/deadLine");
                            }
                            deadLineToSend.remove(lineId);
                        } catch (Exception e) {
                            log.error(e.getMessage());
                        }
                    };
                    basePool.execute(runnable);
                });
            }
        };

        Runnable checkErrorSendLines = () -> {
            if (!errorSendLines.isEmpty()) {
                sendLinesInfo(new ArrayList<>(errorSendLines));
            }
        };

        Runnable checkRateLimit = () -> {
            if (hostConfig.getHost().getVersion() == 1) {
                HashSet<String> dialuppedIdSet = pppoeService.getDialuppedIdSet();
                Set<String> limitedLineIdSet = rateLimitService.getLimitedLineIdSet();
                dialuppedIdSet.removeAll(limitedLineIdSet);
                Set<String> notLimitedIdSet = dialuppedIdSet;
                notLimitedIdSet.forEach(id -> rateLimitService.limit(id));
            }
        };

        scheduler.scheduleAtFixedRate(checkErrorSendLines, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(checkFullDial, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(checkFullSocksStart, 20, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(checkDeadLine, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(keepCPUHealth, 0, 60, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(checkRateLimit, 0, 60, TimeUnit.SECONDS);
    }




    @GetMapping("/all")
    public void getAllLines() {
        basePool.execute(new Runnable() {
            @Override
            public void run() {
                checkAndSendAllLinesInfo();
            }
        });
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
        try {
            int status = HttpRequest.delete(hostConfig.getJavaServerHost() + "/v1.0/server/lines?" + "hostId=" + hostConfig.getHost().getId())
                    .execute().getStatus();
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
            if (dialFalseTimesMap.get(lineId) == checkingTimesOfDefineDeadLine){
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
        basePool.execute(new Runnable() {
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
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    log.info("send Lines Info ...");
                    try {
                        HttpResponse response = HttpRequest.put(hostConfig.getJavaServerHost() + "/v1.0/line")
                                .body(body)
                                .timeout(5000)
                                .execute();
                        boolean status = response.isOk();
                        if (status) {
                            log.info("send Lines Info ok : {}", body);
                            errorSendLines.removeAll(lines);
                        } else {
                            throw new ResponseNotOkException("response not OK in sendLinesInfo to the Java server, API(PUT): /v1.0/line, " + response.body());
                        }
                    } catch (RuntimeException | ResponseNotOkException e) {
                        log.error(e.getMessage());
                        log.info("send Lines Info NOT ok : {}", body);
                        errorSendLines.removeAll(lines);
                        errorSendLines.addAll(lines);
                    }
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
