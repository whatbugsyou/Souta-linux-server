package com.souta.linuxserver.service;

import com.souta.linuxserver.dto.Socks5Info;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface Socks5Service {

    void updateConfig(Socks5Info socks5Info);

    List<Socks5Info> getAllSocks5();

    Socks5Info getSocks5(String ip);

    boolean stopSocks(String id);

    boolean checkConfigFileExist(String id);

    boolean isStart(String id, String ip);
}
