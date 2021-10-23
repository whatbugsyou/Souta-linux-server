package com.souta.linuxserver.service;

import com.souta.linuxserver.dto.Socks5InfoDTO;

import java.util.List;

public interface Socks5Service {

    void updateConfig(Socks5InfoDTO socks5Info);

    List<Socks5InfoDTO> getAllSocks5();

    Socks5InfoDTO getSocks5(String ip);

    boolean stopSocks(String ip);

    boolean checkConfigFileExist(String ip);

    boolean isStart(String id, String ip);
}
