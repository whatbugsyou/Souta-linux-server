package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Socks5;

import java.util.List;

public interface Socks5Service extends SocksService {

    Socks5 getSocks(String id);

    Socks5 getSocks(String id, String ip);

    List<Socks5> getAllListenedSocks();
}
