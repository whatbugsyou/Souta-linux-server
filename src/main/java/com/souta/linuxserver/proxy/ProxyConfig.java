package com.souta.linuxserver.proxy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;


@ConfigurationProperties(prefix = "line")
@Data
public class ProxyConfig {

    @NestedConfigurationProperty
    private Socks5Config socks5Config;

    @NestedConfigurationProperty
    private ShadowsocksConfig shadowsocksConfig;

    @Data
    public static class ShadowsocksConfig {

        private Integer port;

        private String password;

        private String method;

    }

    @Data
    public static class Socks5Config {

        private Integer port;

        private String username;

        private String password;

    }
}
