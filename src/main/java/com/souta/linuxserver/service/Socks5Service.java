package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Socks5;

import java.util.List;

public interface Socks5Service {
    boolean checkConfigFileExist(String id);

    boolean isStart(String id);

    boolean createSocks5ConfigFile(String id);

    Socks5 getSocks5(String id);

    boolean stopSocks5(String id);

    boolean restartSocks5(String id);

    boolean startSocks5(String sid);

    List<Socks5> getAllListenedSocks5();
}
