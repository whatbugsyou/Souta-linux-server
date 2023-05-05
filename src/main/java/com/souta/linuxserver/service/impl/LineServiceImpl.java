package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.line.Line;
import com.souta.linuxserver.line.LineBuilder;
import com.souta.linuxserver.service.*;
import com.souta.linuxserver.util.LineMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.souta.linuxserver.monitor.LineMonitor.deadLineIdSet;
import static com.souta.linuxserver.monitor.LineMonitor.dialingLines;

@Service
public class LineServiceImpl implements LineService {
    private static final Logger log = LoggerFactory.getLogger(LineServiceImpl.class);
    private static final ReentrantLock lock = new ReentrantLock();
    private static boolean onGettingLines;
    private final PPPOEService pppoeService;
    private final LineBuilder lineBuilder;

    public LineServiceImpl(PPPOEService pppoeService, LineBuilder lineBuilder) {
        this.pppoeService = pppoeService;
        this.lineBuilder = lineBuilder;
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
    public List<Line> getLines(Set<String> lineIdList) {
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
                    Line line = lineBuilder.getLine(lineId);
                    if (line == null || line.getOutIpAddr() == null) {
                        log.warn("Line {} is NOT OK", lineId);
                        //TODO  notion the changes
                    } else {
                        log.info("Line {} is OK", lineId);
                        lines.add(line);
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(2L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        onGettingLines = false;
        return new ArrayList<>(lines);
    }


    @Override
    public Line refresh(String lineId) {
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
        return createLine(lineId);
    }

    @Override
    public boolean deleteLine(String lineId) {
        dialingLines.remove(lineId);
        pppoeService.shutDown(lineId);
//        socks5Service.stopSocks(lineId);
//        shadowsocksService.stopSocks(lineId);
//        namespaceService.deleteNameSpace(Namespace.DEFAULT_PREFIX + lineId);
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
