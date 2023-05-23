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

    public String getInboundConfigFilePath(String lineId) {
        return "/root/v2rayConfig/inbound" + lineId + ".json";
    }

    public String getOutboundConfigFilePath(String lineId) {
        return "/root/v2rayConfig/outbound" + lineId + ".json";
    }

    public String getShadowsocksTag(String lineId) {
        return "ss" + lineId;
    }

    public String getSocks5Tag(String lineId) {
        return "socks5" + lineId;
    }

    public String getOutBoundTag(String lineId) {
        return "out" + lineId;
    }

    @Data
    public static class Socks5Config {

        private Integer port;

        private String username;

        private String password;

    }
}
