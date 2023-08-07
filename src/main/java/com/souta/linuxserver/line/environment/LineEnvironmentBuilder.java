package com.souta.linuxserver.line.environment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LineEnvironmentBuilder {
    private final ProxyEnvironmentBuilder proxyEnvironmentBuilder;
    private final PPPEnvironmentBuilder pppEnvironmentBuilder;

    public LineEnvironmentBuilder(ProxyEnvironmentBuilder proxyEnvironmentBuilder, PPPEnvironmentBuilder pppEnvironmentBuilder) {
        this.proxyEnvironmentBuilder = proxyEnvironmentBuilder;
        this.pppEnvironmentBuilder = pppEnvironmentBuilder;
    }

    public boolean build(String lineId) {
        try {
            proxyEnvironmentBuilder.build(lineId);
            pppEnvironmentBuilder.build(lineId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
        return true;
    }


}
