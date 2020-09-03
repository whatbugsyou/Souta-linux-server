package com.souta.linuxserver.entity;

import lombok.*;

@Data
public class Line {
    private String id;
    private Socks5 socks5;
    private Shadowsocks shadowsocks;

    public Line(String id, Socks5 socks5, Shadowsocks shadowsocks) {
        this.id = id;
        this.socks5 = socks5;
        this.shadowsocks = shadowsocks;
    }
}

