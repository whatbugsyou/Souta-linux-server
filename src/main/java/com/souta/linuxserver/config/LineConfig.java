package com.souta.linuxserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;


@ConfigurationProperties(prefix = "line")
@Data
public class LineConfig {
    private String defaultRateLimit;

    @NestedConfigurationProperty
    private Socks5Config socks5Config;

    @NestedConfigurationProperty
    private ShadowsocksConfig shadowsocksConfig;
}
