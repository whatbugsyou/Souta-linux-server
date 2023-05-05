package com.souta.linuxserver.line;

import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.proxy.ProxyConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class LineFactory {
    private final LineBuildConfig lineBuildConfig;
    private ProxyConfig.ShadowsocksConfig shadowsocksConfig;
    private ProxyConfig.Socks5Config socks5Config;

    public LineFactory(LineBuildConfig lineBuildConfig) {
        this.lineBuildConfig = lineBuildConfig;
        shadowsocksConfig = lineBuildConfig.getProxyConfig().getShadowsocksConfig();
        socks5Config = lineBuildConfig.getProxyConfig().getSocks5Config();

    }

    public Line getLine(String lineId) {
        Line line = new Line();
        line.setLineId(lineId);
        line.setNamespaceName(lineBuildConfig.getNamespaceName(lineId));
        line.setVethName(lineBuildConfig.getVethName(lineId));
        line.setAdsl(lineBuildConfig.getADSL(lineId));
        Shadowsocks shadowsocks = new Shadowsocks(shadowsocksConfig.getPassword(), shadowsocksConfig.getMethod());
        shadowsocks.setPort(shadowsocks.getPort());
        Socks5 socks5 = new Socks5(socks5Config.getUsername(), socks5Config.getPassword());
        socks5.setPort(socks5.getPort());
        line.setProxyServers(Arrays.asList(shadowsocks, socks5));
        return line;
    }
}
