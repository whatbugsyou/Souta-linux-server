package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Shadowsocks;

import java.util.List;

public interface ShadowsocksService {
    boolean checkConfigFileExist(String id);

    boolean createShadowsocksConfigfile(String id,String ip);
    boolean createShadowsocksConfigfile(String id);

    boolean stopShadowsocks(String id);

    boolean restartShadowsocks(String id);

    boolean startShadowsocks(String id,String ip);
    boolean startShadowsocks(String id);

    boolean isStart(String id,String ip);
    boolean isStart(String id);

    Shadowsocks getShadowsocks(String id,String ip);
    Shadowsocks getShadowsocks(String id);

    List<Shadowsocks> getAllListenedShadowsocks();
}
