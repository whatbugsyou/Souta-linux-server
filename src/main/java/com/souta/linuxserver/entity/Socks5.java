package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.Data;


@Data
public class Socks5 extends SocksPrototype {
    public static final String  DEFAULT_USERNAME = "test123";
    public static final String  DEFAULT_PASSWORD = "test123";
    public static final String DEFAULT_PORT ="18000";
    private String username;
    private String password;
    public Socks5() {
        setUsername(DEFAULT_USERNAME);
        setPassword(DEFAULT_PASSWORD);
        setPort(DEFAULT_PORT);
    }
}
