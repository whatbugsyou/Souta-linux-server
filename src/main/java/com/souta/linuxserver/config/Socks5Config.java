package com.souta.linuxserver.config;

import lombok.Data;

@Data
public class Socks5Config {

    private Integer port;

    private String username;

    private String password;

}
