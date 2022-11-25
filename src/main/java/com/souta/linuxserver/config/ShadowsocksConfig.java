package com.souta.linuxserver.config;

import lombok.Data;

@Data
public class ShadowsocksConfig {

    private Integer port;

    private String password;

    private String method;

}
