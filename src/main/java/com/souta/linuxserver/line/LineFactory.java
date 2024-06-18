package com.souta.linuxserver.line;


import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.proxy.ProxyConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class LineFactory {
    private final LineBuildConfig lineBuildConfig;
    private ProxyConfig proxyConfig;

    public LineFactory(LineBuildConfig lineBuildConfig, ProxyConfig proxyConfig) {
        this.lineBuildConfig = lineBuildConfig;
        this.proxyConfig = proxyConfig;
    }


    public Line createLine(String lineId) {
        Line line = new Line();
        line.setLineId(lineId);
        line.setNamespaceName(lineBuildConfig.getNamespaceName(lineId));
        line.setVethName(lineBuildConfig.getVethName(lineId));
        line.setAdsl(lineBuildConfig.getADSL(lineId));
        line.setProxyListenIp(lineBuildConfig.getListenIp(lineId));
        line.setProxyNamespaceName(lineBuildConfig.getServerNamespaceName());

        ProxyConfig.ShadowsocksConfig shadowsocksConfig= proxyConfig.getShadowsocksConfig(lineId);
        ProxyConfig.Socks5Config socks5Config= proxyConfig.getSocks5Config(lineId);

        Shadowsocks shadowsocks = new Shadowsocks(shadowsocksConfig.getPassword(), shadowsocksConfig.getMethod());
        shadowsocks.setPort(shadowsocksConfig.getPort().toString());
        Socks5 socks5 = new Socks5(socks5Config.getUsername(), socks5Config.getPassword());
        socks5.setPort(socks5Config.getPort().toString());
        line.setProxyServers(Arrays.asList(shadowsocks, socks5));
        return line;
    }
}
