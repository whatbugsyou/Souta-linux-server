package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.*;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.Socks5Service;
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

    public LineServiceImpl(Socks5Service socks5Service, ShadowsocksService shadowsocksService, PPPOEService pppoeService) {
        this.socks5Service = socks5Service;
        this.shadowsocksService = shadowsocksService;
        this.pppoeService = pppoeService;
    }

    @Autowired
    @Qualifier("linePool")
    private ExecutorService linePool;

    @Override
    public FutureTask<Line> createLine(String lineId) {
        Callable<Line> dialHandle = () -> {
            Line line = getLine(lineId);
            try {
                if (line == null) {
                    PPPOE pppoe = pppoeService.createPPPOE(lineId);
                    if (pppoe == null) return null;
                    dialingLines.add(lineId);
                    FutureTask<PPPOE> futureTask = pppoeService.dialUp(pppoe);
                    PPPOE pppoeR = futureTask.get();
                    String ip;
                    if (pppoeR != null && (ip = pppoeR.getOutIP()) != null) {
                        initSocks(lineId);
                        if (startSocks(lineId, ip)) {
                            int times = 0;
                            Socks5 socks5 = null;
                            Shadowsocks shadowsocks = null;
                            do {
                                if (socks5 == null) {
                                    socks5 = socks5Service.getSocks(lineId, ip);
                                }
                                if (shadowsocks == null) {
                                    shadowsocks = shadowsocksService.getSocks(lineId, ip);
                                }
                                if (socks5 != null && shadowsocks != null) {
                                    line = new Line(lineId, socks5, shadowsocks);
                                    break;
                                }
                            } while (++times < 10);
                            if (line == null) {
                                log.error("line {} create error", lineId);
                                deleteLine(lineId);
                            }
                        }
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

    private void initSocks(String lineId) {
        socks5Service.stopSocks(lineId);
        shadowsocksService.stopSocks(lineId);
    }

    private boolean startSocks(String lineId, String ip) {
        boolean b = socks5Service.startSocks(lineId, ip);
        boolean b1 = shadowsocksService.startSocks(lineId, ip);
        return b && b1;
    }

    @Override
    public Line getLine(String lineId) {
        String ip = pppoeService.getIP(lineId);
        if (ip == null) return null;

        Socks5 socks5 = socks5Service.getSocks(lineId, ip);
        Shadowsocks shadowsocks = shadowsocksService.getSocks(lineId, ip);
        if (socks5 != null && shadowsocks != null) {
            return new Line(lineId, socks5, shadowsocks);
        } else {
            return null;
        }
    }

    @Override
    public List<Line> getLines(Set<String> lineIdList) {
        List<Line> lines = Collections.synchronizedList(new ArrayList());
        try {
            lock.lock();
            onGettingLines = true;
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
                    Line line = getLine(lineId);
                    if (line != null) {
                        log.info("Line {} is OK", lineId);
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
            onGettingLines = false;
            lock.unlock();
        }
        return new ArrayList<Line>(lines);
    }

    @Override
    public FutureTask<Line> refreshLine(String lineId) {
        if (onGettingLines) {
            try {
                //lock till getLine thread invoking
                lock.lock();
            } finally {
                lock.unlock();
            }
        }
        boolean add = dialingLines.add(lineId);
        if (!add) return null;
        socks5Service.stopSocks(lineId);
        shadowsocksService.stopSocks(lineId);
        pppoeService.shutDown(lineId);
        try {
            TimeUnit.SECONDS.sleep(lineRedialWait);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return createLine(lineId);
    }

    @Override
    public boolean deleteLine(String lineId) {
        socks5Service.stopSocks(lineId);
        shadowsocksService.stopSocks(lineId);
        dialingLines.remove(lineId);
        pppoeService.shutDown(lineId);
        return true;
    }

    @Override
    public boolean editProtoInLine(String lineId, String protoId, String action) {
        if (!pppoeService.isDialUp(lineId)) {
            return false;
        } else {
            if (protoId.equals("socks5")) {
                if (action.equals("on")) {
                    socks5Service.createConfigFile(lineId);
                    socks5Service.startSocks(lineId);
                } else if (action.equals("off")) {
                    socks5Service.stopSocks(lineId);
                }
            } else {
                if (action.equals("on")) {
                    shadowsocksService.createConfigFile(lineId);
                    shadowsocksService.startSocks(lineId);
                } else if (action.equals("off")) {
                    shadowsocksService.stopSocks(lineId);
                }
            }
            return true;
        }
    }

    @Override
    public boolean checkExits(String lineId) {
        if (!pppoeService.isDialUp(lineId)) {
            return false;
        }
        String ip = pppoeService.getIP(lineId);
        boolean startShadowscocks = shadowsocksService.isStart(lineId, ip);
        boolean startSocks5 = socks5Service.isStart(lineId, ip);
        return startShadowscocks && startSocks5;
    }

    @Override
    public String generateLineID() {
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
}
