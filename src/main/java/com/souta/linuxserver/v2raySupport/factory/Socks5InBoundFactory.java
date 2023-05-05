package com.souta.linuxserver.v2raySupport.factory;

import com.souta.linuxserver.v2raySupport.InBoundObject;
import com.souta.linuxserver.v2raySupport.socks5.AccountObject;
import com.souta.linuxserver.v2raySupport.socks5.Socks5Settings;

public class Socks5InBoundFactory {
    public InBoundObject getInstance(String listenIp, int port, String user, String pass, String tag) {
        InBoundObject result = new InBoundObject();

        Socks5Settings settings = new Socks5Settings();
        AccountObject accountObject = new AccountObject(user, pass);
        settings.setAuth("password");
        settings.setUserLevel(0);
        settings.setAccountObject(new AccountObject[]{accountObject});
        settings.setUdp(true);

        result.setSettings(settings);
        result.setListen(listenIp);
        result.setProtocol("socks");
        result.setTag(tag);
        result.setPort(port);

        return result;
    }
}
