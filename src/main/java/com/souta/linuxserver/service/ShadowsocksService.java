package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Shadowsocks;

import java.util.List;

public interface ShadowsocksService extends SocksService {

    Shadowsocks getSocks(String id, String ip);

    Shadowsocks getSocks(String id);

    List<Shadowsocks> getAllListenedShadowsocks();
}
