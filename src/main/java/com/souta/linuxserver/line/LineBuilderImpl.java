package com.souta.linuxserver.line;

import com.souta.linuxserver.ppp.PPPEnvironmentBuilder;
import com.souta.linuxserver.proxy.ProxyEnvironmentBuilder;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component("lineBuilder")
@Scope("prototype")
@Slf4j
public class LineBuilderImpl implements LineBuilder {
    private static final ConcurrentHashMap<String, Line> lineMap = new ConcurrentHashMap<>();
    private final PPPOEService pppoeService;
    private final ProxyEnvironmentBuilder proxyEnvironmentBuilder;
    private final PPPEnvironmentBuilder pppEnvironmentBuilder;
    private final LineFactory lineFactory;

    public LineBuilderImpl(PPPOEService pppoeService, ProxyEnvironmentBuilder proxyEnvironmentBuilder, PPPEnvironmentBuilder pppEnvironmentBuilder, LineFactory lineFactory) {
        this.pppoeService = pppoeService;
        this.proxyEnvironmentBuilder = proxyEnvironmentBuilder;
        this.pppEnvironmentBuilder = pppEnvironmentBuilder;
        this.lineFactory = lineFactory;
    }


    @Override
    public Line build(String lineId) {
        Line line = getLine(lineId);
        String ip = dialupPPPoE(line);
        line.setOutIpAddr(ip);
        return line;
    }

    @Override
    public Line getLine(String lineId) {
        Line line = lineMap.get(lineId);
        if (line == null) {
            try {
                buildEnvironment(lineId);
            } catch (Exception e) {
                log.error(e.getMessage());
                return null;
            }
        }
        line = lineFactory.getLine(lineId);
        lineMap.put(lineId, line);
        String ip = pppoeService.getIP(lineId);
        line.setOutIpAddr(ip);
        return line;
    }

    private void buildEnvironment(String lineId) throws NamespaceNotExistException {
        boolean pppE = pppEnvironmentBuilder.check(lineId);
        boolean proxyE = proxyEnvironmentBuilder.check(lineId);
        if (!proxyE) {
            proxyEnvironmentBuilder.build(lineId);
        }
        if (!pppE) {
            pppEnvironmentBuilder.build(lineId);
        }
    }

    public String dialupPPPoE(Line line) {
        return pppoeService.dialUp(line.getLineId(), line.getAdsl().getAdslUser(), line.getAdsl().getAdslPassword(), line.getVethName(), line.getNamespaceName());
    }
}
