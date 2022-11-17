package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.controller.Host;
import com.souta.linuxserver.entity.*;
import com.souta.linuxserver.entity.abs.Socks;
import com.souta.linuxserver.service.*;
import com.souta.linuxserver.util.LineMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LineServiceImpl implements LineService {
    private static final Logger log = LoggerFactory.getLogger(LineServiceImpl.class);
    private static final ReentrantLock lock = new ReentrantLock();
    private static boolean onGettingLines;
    private final Socks5Service socks5Service;
    private final ShadowsocksService shadowsocksService;
    private final PPPOEService pppoeService;
    private final NamespaceService namespaceService;
    @Autowired
    @Qualifier("linePool")
    private ExecutorService linePool;
    private RateLimitServiceImpl rateLimitService;
    private Integer MAX_RATE_LIMIT_KB = 300;

    public LineServiceImpl(@Qualifier("v2raySocks5ServiceImpl") Socks5Service socks5Service, ShadowsocksService shadowsocksService, PPPOEService pppoeService, NamespaceService namespaceService, RateLimitServiceImpl rateLimitService) {
        this.socks5Service = socks5Service;
        this.shadowsocksService = shadowsocksService;
        this.pppoeService = pppoeService;
        this.namespaceService = namespaceService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public FutureTask<Line> createLineWithDefaultListenIP(String lineId) {
        Callable<Line> dialHandle = () -> {
            Line line = null;
            try {
                PPPOE pppoe = pppoeService.createPPPOE(lineId);
                if (pppoe == null) {
                    return null;
                }
                dialingLines.add(lineId);
                FutureTask<PPPOE> futureTask = pppoeService.dialUp(pppoe);
                PPPOE pppoeR = futureTask.get();
                String outIp;
                if (pppoeR != null && (outIp = pppoeR.getOutIP()) != null) {
                    log.info("line {} start socks", lineId);
                    int times = 1;
                    Socks5 socks5 = null;
                    Shadowsocks shadowsocks = null;
                    do {
                        if (socks5 == null) {
                            Socks socks = socks5Service.getSocks(lineId, DEFAULT_LISTEN_IP);
                            if (socks != null) {
                                socks5 = (Socks5) socks;
                                socks5.setIp(outIp);
                            }
                        }
                        if (shadowsocks == null) {
                            Socks socks = shadowsocksService.getSocks(lineId, DEFAULT_LISTEN_IP);
                            if (socks != null) {
                                shadowsocks = (Shadowsocks) socks;
                                shadowsocks.setIp(outIp);
                            }
                        }
                        if (socks5 != null && shadowsocks != null) {
                            line = new Line(lineId, socks5, shadowsocks, pppoeService.getADSLList().get(Integer.valueOf(lineId) - 1).getAdslUser(), pppoeService.getADSLList().get(Integer.valueOf(lineId) - 1).getAdslPassword());
                            log.info("line {} start socks ok", lineId);
                            break;
                        } else if (times == 1) {
                            startSocks(lineId, DEFAULT_LISTEN_IP);
                        }
                        log.info("line {} check socks: {}/10", lineId, times);
                        Thread.sleep(500);
                    } while (++times <= 10);
                    if (line == null) {
                        log.error("line {} create error", lineId);
                        deleteLine(lineId);
                    }
                    if (Host.VERSION == 1) {
                        rateLimitService.limit(lineId, MAX_RATE_LIMIT_KB);
                    }
                }
            } finally {
                dialingLines.remove(lineId);
            }
            return line;
        };
        FutureTask<Line> futureTask = new FutureTask(dialHandle);
        linePool.submit(futureTask);
        return futureTask;
    }

    private boolean startSocks(String lineId, String ip) {
        boolean b = socks5Service.startSocks(lineId, ip);
        boolean b1 = shadowsocksService.startSocks(lineId, ip);
        return b && b1;
    }

    @Override
    public Line getLine(String lineId, String listenIp) {
        if (listenIp == null) {
            return null;
        }
        Socks5 socks5 = (Socks5) socks5Service.getSocks(lineId, listenIp);
        Shadowsocks shadowsocks = (Shadowsocks) shadowsocksService.getSocks(lineId, listenIp);
        if (socks5 != null && shadowsocks != null) {
            return new Line(lineId, socks5, shadowsocks, pppoeService.getADSLList().get(Integer.valueOf(lineId) - 1).getAdslUser(), pppoeService.getADSLList().get(Integer.valueOf(lineId) - 1).getAdslPassword());
        } else {
            return null;
        }
    }

    @Override
    public Line getLineWithDefaultListenIP(String lineId) {
        return getLine(lineId, DEFAULT_LISTEN_IP);
    }

    @Override
    public List<Line> getLinesWithDefaultListenIP(Set<String> lineIdList) {
        onGettingLines = true;
        List<Line> lines = Collections.synchronizedList(new ArrayList());
        lock.lock();
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
                    String outIP = pppoeService.getIP(lineId);
                    if (outIP == null || outIP.isEmpty()) {
                        if (!dialingLines.contains(lineId)) {
                            deleteLine(lineId);
                        }
                        return;
                    }
                    Line line = getLine(lineId, DEFAULT_LISTEN_IP);
                    if (line != null) {
                        log.info("Line {} is OK", lineId);
                        line.getShadowsocks().setIp(outIP);
                        line.getSocks5().setIp(outIP);
                        lines.add(line);
                    } else {
                        log.warn("Line {} is NOT OK", lineId);
                        deleteLine(lineId);
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(2l, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        onGettingLines = false;
        return new ArrayList<Line>(lines);
    }

    @Override
    public FutureTask<Line> refreshLineWithDefaultListenIP(String lineId) {
        if (onGettingLines) {
            //lock till getLine thread invoking
            lock.lock();
            try {
                // do something
            } finally {
                lock.unlock();
            }
        }
        boolean add = dialingLines.add(lineId);
        if (!add) {
            return null;
        }
        pppoeService.shutDown(lineId);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return createLineWithDefaultListenIP(lineId);
    }

    @Override
    public boolean deleteLine(String lineId) {
        dialingLines.remove(lineId);
        pppoeService.shutDown(lineId);
        socks5Service.stopSocks(lineId);
        shadowsocksService.stopSocks(lineId);
        namespaceService.deleteNameSpace("ns" + lineId);
        return true;
    }

    @Override
    public boolean editProtoInLineWithDefaultListenIP(String lineId, String protoId, String action) {
        if (!pppoeService.isDialUp(lineId)) {
            return false;
        } else {
            if (protoId.equals("socks5")) {
                if (action.equals("on")) {
                    socks5Service.startSocks(lineId, DEFAULT_LISTEN_IP);
                } else if (action.equals("off")) {
                    socks5Service.stopSocks(lineId);
                }
            } else {
                if (action.equals("on")) {
                    shadowsocksService.startSocks(lineId, DEFAULT_LISTEN_IP);
                } else if (action.equals("off")) {
                    shadowsocksService.stopSocks(lineId);
                }
            }
            return true;
        }
    }

    @Override
    public boolean checkExitsWithDefaultListenIP(String lineId) {
        if (!pppoeService.isDialUp(lineId)) {
            return false;
        }
//        String ip = pppoeService.getIP(lineId);
        boolean startShadowscocks = shadowsocksService.isStart(lineId, DEFAULT_LISTEN_IP);
        boolean startSocks5 = socks5Service.isStart(lineId, DEFAULT_LISTEN_IP);
        return startShadowscocks && startSocks5;
    }

    @Override
    public String generateLineID() {
        LineMax lineMax = new LineMax();
        HashSet<String> dialuppedIdSet = pppoeService.getDialuppedIdSet();
        Set<String> excludeSet;
        excludeSet = dialuppedIdSet;
        excludeSet.addAll(dialingLines);
        excludeSet.addAll(deadLineIdSet);
        List<ADSL> adslList = pppoeService.getADSLList();
        if (excludeSet.size() < adslList.size()) {
            for (String id :
                    excludeSet) {
                lineMax.add(Integer.parseInt(id));
            }
            return String.valueOf(lineMax.getMax());
        }
        return null;
    }
}
