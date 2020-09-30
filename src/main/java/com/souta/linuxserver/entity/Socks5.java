package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.Data;

import java.util.Objects;

@Data
public class Socks5 extends SocksPrototype {
    private String id;
    private String ip;
    private String port;
    private String username;
    private String password;
    private String pid;


    public Socks5() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Socks5 socks5 = (Socks5) o;
        return id.equals(socks5.id) &&
                Objects.equals(ip, socks5.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ip);
    }
}
