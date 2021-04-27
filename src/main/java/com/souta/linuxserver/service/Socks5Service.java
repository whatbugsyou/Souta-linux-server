package com.souta.linuxserver.service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface Socks5Service extends SocksService {

    Set<String> onStartingSocks = new CopyOnWriteArraySet();

    HashSet<String> getStartedIdSet();
}
