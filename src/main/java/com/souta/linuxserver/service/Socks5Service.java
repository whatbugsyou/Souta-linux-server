package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Socks5;

import java.util.List;

public interface Socks5Service {
    boolean checkConfigFileExist(String id);

    boolean isStart(String id,String ip);
    boolean isStart(String id);

    boolean createSocks5ConfigFile(String id);
    boolean createSocks5ConfigFile(String id,String ip );

    Socks5 getSocks5(String id);
    Socks5 getSocks5(String id,String ip);

    boolean stopSocks5(String id);

    boolean restartSocks5(String id);

    boolean startSocks5(String sid);
    boolean startSocks5(String sid,String ip);

    List<Socks5> getAllListenedSocks5();
}
