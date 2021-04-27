package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.abs.Socks;

public interface SocksService {
    boolean checkConfigFileExist(String id);

    boolean isStart(String id, String ip);

    boolean createConfigFile(String id, String ip);

    Socks getSocks(String id, String ip);

    boolean stopSocks(String id);

    boolean restartSocks(String id ,String listenIp);

    boolean startSocks(String sid, String ip);
}
