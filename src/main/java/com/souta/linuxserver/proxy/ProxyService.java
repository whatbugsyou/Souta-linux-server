package com.souta.linuxserver.proxy;

public interface ProxyService {
    void startProxy(String proxyId, String listenIp, String namespaceName);

    boolean isProxyStart(String listenIp, String namespaceName);

    void stopProxy(String proxyId, String namespaceName);
}
