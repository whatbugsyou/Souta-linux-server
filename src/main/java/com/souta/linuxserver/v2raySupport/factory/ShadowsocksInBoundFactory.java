package com.souta.linuxserver.v2raySupport.factory;

import com.souta.linuxserver.v2raySupport.InBoundObject;
import com.souta.linuxserver.v2raySupport.shadowsocks.ShadowsocksSettings;

public class ShadowsocksInBoundFactory {
    public InBoundObject getInstance(String listenIp, int port, String method, String password, String tag) {
        InBoundObject result = new InBoundObject();
        ShadowsocksSettings settings = new ShadowsocksSettings();

        settings.setPassword(password);
        settings.setMethod(method);
        settings.setUserLevel(0);
        settings.setNetwork("tcp,udp");
        settings.setOta(false);

        result.setSettings(settings);
        result.setListen(listenIp);
        result.setProtocol("shadowsocks");
        result.setTag(tag);
        result.setPort(port);

        return result;
    }
}
