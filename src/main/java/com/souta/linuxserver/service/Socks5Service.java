package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Socks5;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface Socks5Service extends SocksService {

    Set<String> onStartingSocks = new CopyOnWriteArraySet();

    Socks5 getSocks(String id);

    Socks5 getSocks(String id, String ip);

    List<Socks5> getAllListenedSocks();

    HashSet<String> getStartedIdSet();
}
