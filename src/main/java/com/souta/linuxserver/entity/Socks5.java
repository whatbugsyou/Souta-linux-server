package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.Data;

import java.util.Objects;

@Data
public class Socks5 extends SocksPrototype {
    private String username;
    private String password;
    public Socks5() {
    }
}
