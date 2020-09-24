package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.controller.MainController;
import com.souta.linuxserver.entity.Line;
import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.Socks5Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class LineServiceImpl implements LineService {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final Socks5Service socks5Service;
    private final ShadowsocksService shadowsocksService;
    private final PPPOEService pppoeService;
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    public LineServiceImpl(Socks5Service socks5Service, ShadowsocksService shadowsocksService, PPPOEService pppoeService) {
        this.socks5Service = socks5Service;
        this.shadowsocksService = shadowsocksService;
        this.pppoeService = pppoeService;
    }

    @Override
    public FutureTask<Line> createLine(String lineId) {
        Callable<Line> dialHandle = () -> {
            Line line = getLine(lineId);
            if (line == null){
                PPPOE pppoe = pppoeService.createPPPOE(lineId);
                dialingLines.add(lineId);
                FutureTask<PPPOE> futureTask = pppoeService.dialUp(pppoe);
                PPPOE pppoeR = futureTask.get();
                String ip;
                if (pppoeR != null && (ip=pppoeR.getOutIP()) != null) {
                    if (startSocks(lineId,ip)) {
                        int times = 0;
                         do {
                             Socks5 socks5 = socks5Service.getSocks5(lineId,ip);
                             Shadowsocks shadowsocks = shadowsocksService.getShadowsocks(lineId,ip);
                             if (socks5.getPid()!=null && shadowsocks.getPid()!=null){
                                 line =new Line(lineId,socks5,shadowsocks);
                                 break;
                             }
                             TimeUnit.MILLISECONDS.sleep(100);
                        }while(++times < 10);

                        if (line==null){
                            deleteLine(lineId);
                        }
                    }
                }
                dialingLines.remove(lineId);
            }
            return line;
        };
        FutureTask<Line> futureTask = new FutureTask(dialHandle);
        pool.execute(futureTask);
        return futureTask;
    }

    private boolean startSocks(String lineId, String ip) {
        boolean b = socks5Service.startSocks5(lineId,ip);
        boolean b1 = shadowsocksService.startShadowsocks(lineId,ip);
        return b && b1;
    }

    @Override
    public Line getLine(String lineId) {
        String ip = pppoeService.getIP(lineId);
        if (ip==null) return null;

        Socks5 socks5 = socks5Service.getSocks5(lineId,ip);
        Shadowsocks shadowsocks = shadowsocksService.getShadowsocks(lineId,ip);
        if (socks5.getPid()!=null && shadowsocks.getPid()!=null){
            return new Line(lineId, socks5, shadowsocks);
        } else {
            return null;
        }
    }

    @Override
    public FutureTask<Line> refreshLine(String lineId) {
        if (!dialingLines.contains(lineId)) {
            deleteLine(lineId);
            dialingLines.add(lineId);
            try {
                TimeUnit.SECONDS.sleep(lineRedialWait);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return createLine(lineId);
        }
        return null;
    }

    @Override
    public boolean deleteLine(String lineId) {
        socks5Service.stopSocks5(lineId);
        shadowsocksService.stopShadowsocks(lineId);
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
            return true;
        }
    }

    @Override
    public boolean checkExits(String lineId) {
        if (!pppoeService.isDialUp(lineId)){
            return false;
        }
        String ip = pppoeService.getIP(lineId);
        boolean startShadowscocks = shadowsocksService.isStart(lineId,ip);
        boolean startSocks5 = socks5Service.isStart(lineId,ip);
        return startShadowscocks && startSocks5;
    }
}
