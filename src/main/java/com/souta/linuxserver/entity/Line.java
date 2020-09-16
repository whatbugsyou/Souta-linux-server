package com.souta.linuxserver.entity;

import lombok.Data;

import java.util.Objects;

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

