package com.souta.linuxserver.line;

import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.adsl.ADSLConfigManager;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Veth;
import com.souta.linuxserver.proxy.ProxyConfig;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class LineBuildConfig {
    private static final String serverNamespaceName = "serverSpace";
    private static final String serverLan = "192.168.id.2";
    private static final String vethLan = "192.168.id.1";
    private static final String serverEth = "S-{linkEth}";
    private final ADSLConfigManager adslConfigManager;
    private final ProxyConfig proxyConfig;

    public LineBuildConfig(ADSLConfigManager adslConfigManager, ProxyConfig proxyConfig) {
        this.adslConfigManager = adslConfigManager;
        this.proxyConfig = proxyConfig;
    }

    public String getNamespaceName(String lineId) {
        return Namespace.DEFAULT_PREFIX + lineId;
    }

    public String getServerNamespaceName() {
        return serverNamespaceName;
    }

    public String getVethName(String LineId) {
        return Veth.DEFAULT_PREFIX + LineId;
    }


    public String getListenIp(String lineId) {
        return serverLan.replace("id", lineId);
    }

    public String getVethLan(String lineId) {
        return vethLan.replace("id", lineId);
    }

    public String getServerEthName(String physicalEthName) {
        return serverEth.replace("{linkEth}", physicalEthName);

    }

    public ADSL getADSL(String lineId) {
        return adslConfigManager.getADSL(Integer.valueOf(lineId) - 1);
    }

    public Iterator<ADSL> getADSLIterator() {
        return adslConfigManager.getADSLIterrator();
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }



}
