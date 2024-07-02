package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.adsl.ADSLConfigManager;
import com.souta.linuxserver.line.Line;
import com.souta.linuxserver.line.LineBuilder;
import com.souta.linuxserver.proxy.ProxyService;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.souta.linuxserver.monitor.LineMonitor.creatingLines;

@Service
public class LineServiceImpl implements LineService {
    private static final Logger log = LoggerFactory.getLogger(LineServiceImpl.class);
    private final PPPOEService pppoeService;
    private final LineBuilder lineBuilder;
    private CountDownLatch countDownLatch;

    private ProxyService proxyService;

    private final ADSLConfigManager adslConfigManager;

    public LineServiceImpl(PPPOEService pppoeService, LineBuilder lineBuilder, ProxyService proxyService, ADSLConfigManager adslConfigManager) {
        this.pppoeService = pppoeService;
        this.lineBuilder = lineBuilder;
        this.proxyService = proxyService;
        this.adslConfigManager = adslConfigManager;
    }

    @Override
    public Line createLine(String lineId) {
        return lineBuilder.build(lineId);
    }


    @Override
    public Line getLine(String lineId) {
        return lineBuilder.getLine(lineId);
    }

    @Override
    public synchronized List<Line> getLines(Set<String> lineIdList) {
        countDownLatch = new CountDownLatch(1);
        List<Line> lines = Collections.synchronizedList(new ArrayList());
        try {
            //sort id (String type)
            TreeSet<Integer> integers = new TreeSet<>();
            for (String id : lineIdList
            ) {
                integers.add(Integer.valueOf(id));
            }
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (Integer id : integers
            ) {
                executorService.submit(() -> {
                    String lineId = id.toString();
                    Line line = lineBuilder.getLine(lineId);
                    if (line == null || line.getOutIpAddr() == null || !line.isProxyOn()) {
                        log.warn("Line {} is NOT OK", lineId);
                        deleteLine(lineId);
                    } else {
                        log.info("Line {} is OK", lineId);
                        lines.add(line);
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(2L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            countDownLatch.countDown();
        }
        return new ArrayList<>(lines);
    }

    @Override
    public List<Line> getAvailableLines() {
        HashSet<String> dialuppedIdSet = pppoeService.getDialuppedIdSet();
        return getLines(dialuppedIdSet);
    }


    @Override
    public Line refresh(String lineId) {
        if (countDownLatch != null) {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        proxyService.stopProxy(lineId, getLine(lineId).getProxyNamespaceName());
        pppoeService.shutDown(lineId);
        return createLine(lineId);
    }

    @Override
    public boolean deleteLine(String lineId) {
        creatingLines.remove(lineId);
        proxyService.stopProxy(lineId, getLine(lineId).getProxyNamespaceName());
        pppoeService.shutDown(lineId);
        return true;
    }

    @Override
    public boolean editProtoInLine(String lineId, String protoId, String action) {
        if (!pppoeService.isDialUp(lineId)) {
            return false;
        } else {
//            if (protoId.equals("socks5")) {
//                if (action.equals("on")) {
//                    socks5Service.startSocks(lineId, DEFAULT_LISTEN_IP);
//                } else if (action.equals("off")) {
//                    socks5Service.stopSocks(lineId);
//                }
//            } else {
//                if (action.equals("on")) {
//                    shadowsocksService.startSocks(lineId, DEFAULT_LISTEN_IP);
//                } else if (action.equals("off")) {
//                    shadowsocksService.stopSocks(lineId);
//                }
//            }
            return true;
        }
    }

}
