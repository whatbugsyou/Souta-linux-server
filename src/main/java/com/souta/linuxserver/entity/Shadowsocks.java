package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.Data;

import java.util.Objects;

@Data
public class Shadowsocks extends SocksPrototype {
    private String ip;
    private String port;
    private String password;
    private String encryption;
    private String pid;
    private String id;


    public Shadowsocks() {
    }

    public Shadowsocks(String ip, String port, String password, String encryption, String pid) {
        this.ip = ip;
        this.port = port;
        this.password = password;
        this.encryption = encryption;
        this.pid = pid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shadowsocks that = (Shadowsocks) o;
        return Objects.equals(ip, that.ip) &&
                id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, id);
    }
}
