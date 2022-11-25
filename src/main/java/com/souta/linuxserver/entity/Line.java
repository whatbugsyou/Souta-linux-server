package com.souta.linuxserver.entity;

import lombok.Data;

import java.util.Objects;

@Data
public class Line {

    private String id;
    private Socks5 socks5;
    private Shadowsocks shadowsocks;
    private String adslAccount;
    private String adslPassword;

    public Line(String id, Socks5 socks5, Shadowsocks shadowsocks, String adslAccount, String adslPassword) {
        this.id = id;
        this.socks5 = socks5;
        this.shadowsocks = shadowsocks;
        this.adslAccount = adslAccount;
        this.adslPassword = adslPassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Line line = (Line) o;
        return Objects.equals(id, line.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

