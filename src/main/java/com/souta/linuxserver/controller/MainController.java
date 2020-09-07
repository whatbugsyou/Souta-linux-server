package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.entity.*;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.Socks5Service;
import com.souta.linuxserver.util.LineMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/v1.0/line/notify")
public class MainController {
    @Autowired
    private PPPOEService pppoeService;
    @Autowired
    private ShadowsocksService shadowsocksService;
    @Autowired
    private Socks5Service socks5Service;

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final int lineRedialWait = 2;
    private static final int deadLineLimitTimes = 3;
    private static Set<String> dialingLines = new CopyOnWriteArraySet();
    private static HashMap<String, Integer> redialCheckMap = new HashMap<>();
    private static ExecutorService executorService = Executors.newCachedThreadPool();

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
                dialingLines.add(lineID);
                log.info("LineMonitor is going to create line{} after {} seconds...", lineID, lineRedialWait);
                try {
                    TimeUnit.SECONDS.sleep(lineRedialWait);
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
            newlineIdList.addAll(dialingLines);
            if (newlineIdList.size() < pppoeService.getADSLList().size()) {
                executorService.execute(addOneDial);
            }
        };
        Runnable checkDeadLine = () -> {
            Set<Map.Entry<String, Integer>> entries = redialCheckMap.entrySet();
            for (Map.Entry<String, Integer> entry : entries
            ) {
                Integer value = entry.getValue();
                if (value == deadLineLimitTimes) {
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
                    new Thread(() -> HttpRequest.post(Host.java_server_host + "/v1.0/deadLine")
                            .body(body)
                            .execute()).start();
                }
            }
        };
        scheduler.scheduleAtFixedRate(checkFullDial, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(checkDeadLine, 0, 30, TimeUnit.SECONDS);
    }

    private boolean checkLineIsError(String lineId) {
        Integer integer = redialCheckMap.get(lineId);
        if (integer != null) {
            if (integer >= 5) {
                return true;
            }
        }
        return false;
    }

    private void initSendLineInfo() {
        log.info("initLineInfo....");
        HashSet<String> lineIdSet = pppoeService.getDialuppedIdSet();
        ArrayList<Line> lines = getLines(lineIdSet);
        log.info("total {} lines is ok", lines.size());
        HttpRequest.delete(Host.java_server_host + "/v1.0/server/lines?" + "hostId=" + Host.id)
                .execute().getStatus();
        sendLinesInfo(lines);
    }

    private String GenerateLineID() {
        LineMax lineMax = new LineMax();
        HashSet<String> dialuppedId = pppoeService.getDialuppedIdSet();
        dialuppedId.addAll(dialingLines);
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
            Socks5 socks5 = socks5Service.getSocks5(lineId);
            Shadowsocks shadowsocks = shadowsocksService.getShadowsocks(lineId);
            if (socks5.getIp() != null && shadowsocks.getIp() != null) {
                log.info("Line {} is ok", lineId);
                Line line = new Line(lineId, socks5, shadowsocks);
                lines.add(line);
            } else {
                log.error("Line {} is not ok", lineId);
                deleteLine(lineId);
            }
        }
        return lines;
    }

    private void sendLinesInfo(ArrayList<Line> lines) {
        if (!lines.isEmpty()) {
            HashMap<String, Object> data = new HashMap<>();
            data.put("hostId", Host.id);
            data.put("lines", lines);
            String body = new JSONObject(data).toJSONString();
            log.info("send Lines Info :");
            log.info(body);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpRequest.put(Host.java_server_host + "/v1.0/line")
                            .body(body)
                            .execute().getStatus();
                }
            }).start();
        }
    }

    private void sendLinesInfo(HashSet<String> lineIdSet) {
        ArrayList<Line> lines = getLines(lineIdSet);
        sendLinesInfo(lines);
    }

    private void sendLinesInfo(String lineId) {
        HashSet<String> lineIdSet = new HashSet<>();
        lineIdSet.add(lineId);
        sendLinesInfo(lineIdSet);
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
        boolean start = shadowsocksService.isStart(lineId);
        boolean start1 = socks5Service.isStart(lineId);
        if (start1 && start) {
            sendLinesInfo(lineId);
            resultMap.put("status", "exist");
            return resultMap;
        } else {
            PPPOE pppoe = pppoeService.createPPPOE(lineId);
            FutureTask<PPPOE> futureTask = pppoeService.dialUp(pppoe);
            dialingLines.add(pppoe.getId());
            Runnable dialHandle = new Runnable() {
                @Override
                public void run() {
                    try {
                        PPPOE pppoe = futureTask.get();
                        if (pppoe!=null && pppoe.getOutIP() == null) {
                            Integer integer = redialCheckMap.get(pppoe.getId());
                            if (integer != null) {
                                redialCheckMap.put(pppoe.getId(), integer + 1);
                            } else {
                                redialCheckMap.put(pppoe.getId(), 1);
                            }
                        } else {
                            redialCheckMap.remove(pppoe.getId());
                        }
                        startSocks(lineId);
                        sendLinesInfo(lineId);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        dialingLines.remove(pppoe.getId());
                    }
                }
            };
            executorService.execute(dialHandle);
        }
        resultMap.put("status", "ok");
        return resultMap;
    }

    private void startSocks(String lineId) {
        shadowsocksService.createShadowsocksConfigfile(lineId);
        shadowsocksService.startShadowsocks(lineId);
        socks5Service.createSocks5ConfigFile(lineId);
        socks5Service.startSocks5(lineId);

    }

    @PutMapping
    public HashMap<String, Object> refreshLine(String lineId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        boolean exist = pppoeService.checkConfigFileExist(lineId);
        if (exist) {
            log.info("refresh Line {}", lineId);
            deleteLine(lineId);
            dialingLines.add(lineId);
            new Thread(() -> {
                try {
                    TimeUnit.SECONDS.sleep(lineRedialWait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                createLine(lineId);
            }).start();
            resultMap.put("status", "ok");
            return resultMap;
        } else {
            resultMap.put("status", "not exist");
            return resultMap;
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

    @DeleteMapping
    public HashMap<String, Object> deleteLine(String lineId) {
        log.info("delete line {}", lineId);
        HashMap<String, Object> resultMap = new HashMap<>();
        socks5Service.stopSocks5(lineId);
        shadowsocksService.stopShadowsocks(lineId);
        pppoeService.shutDown(lineId);
        resultMap.put("status", "ok");
        return resultMap;
    }

    @GetMapping("/proto")
    public HashMap<String, Object> proto(String lineId, String protoId, String action) {
        HashMap<String, Object> resultMap = new HashMap<>();
        if (!pppoeService.isDialUp(lineId)) {
            resultMap.put("status", "not exist");
            return resultMap;
        } else {
            log.info("proto change : line {} , {} ,{}", lineId, protoId, action);
            if (protoId.equals("socks5")) {
                socks5Service.createSocks5ConfigFile(lineId);
                if (action.equals("on")) {
                    socks5Service.startSocks5(lineId);
                } else if (action.equals("off")) {
                    socks5Service.stopSocks5(lineId);
                }
            } else {
                shadowsocksService.createShadowsocksConfigfile(lineId);
                if (action.equals("on")) {
                    shadowsocksService.startShadowsocks(lineId);
                } else if (action.equals("off")) {
                    shadowsocksService.stopShadowsocks(lineId);
                }
            }
            resultMap.put("status", "ok");
            return resultMap;
        }
    }

}
