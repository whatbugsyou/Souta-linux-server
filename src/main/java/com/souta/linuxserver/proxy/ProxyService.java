package com.souta.linuxserver.proxy;

public interface ProxyService {

    void startProxy(String lineId);

    boolean isProxyStart(String lineId);

}
