package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.Data;

import java.util.Objects;

@Data
public class Shadowsocks extends SocksPrototype {
    private String password;
    private String encryption;
    public Shadowsocks() {
    }
}
