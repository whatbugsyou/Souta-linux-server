package com.souta.linuxserver.v2raySupport.factory;

import com.souta.linuxserver.v2raySupport.OutBoundObject;
import com.souta.linuxserver.v2raySupport.freedom.FreedomSettings;
import org.springframework.stereotype.Component;


public class FreedomOutBoundFactory {
    public OutBoundObject getInstance(String sendThrough, String domainStrategy, String tag) {
        OutBoundObject result = new OutBoundObject();

        FreedomSettings settings = new FreedomSettings();
        settings.setUserLevel(0);
        settings.setDomainStrategy(domainStrategy);

        result.setSendThrough(sendThrough);
        result.setSettings(settings);
        result.setProtocol("freedom");
        result.setTag(tag);

        return result;
    }
}
