package com.souta.linuxserver.line;

import com.souta.linuxserver.ppp.PPPEnvironmentBuilder;
import com.souta.linuxserver.proxy.ProxyEnvironmentBuilder;
import com.souta.linuxserver.proxy.ProxyService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.exception.NamespaceNotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component("lineBuilder")
@Slf4j
public class LineBuilderImpl implements LineBuilder {
    private static final ConcurrentHashMap<String, Line> lineMap = new ConcurrentHashMap<>();
    private final PPPOEService pppoeService;
    private final LineFactory lineFactory;
    private final ProxyEnvironmentBuilder proxyEnvironmentBuilder;
    private final PPPEnvironmentBuilder pppEnvironmentBuilder;
    private final ProxyService proxyService;

    public LineBuilderImpl(PPPOEService pppoeService, LineFactory lineFactory, ProxyEnvironmentBuilder proxyEnvironmentBuilder, PPPEnvironmentBuilder pppEnvironmentBuilder, ProxyService proxyService) {
        this.pppoeService = pppoeService;
        this.lineFactory = lineFactory;
        this.proxyEnvironmentBuilder = proxyEnvironmentBuilder;
        this.pppEnvironmentBuilder = pppEnvironmentBuilder;
        this.proxyService = proxyService;
    }


    @Override
    public Line build(String lineId) {
        Line line = getLine(lineId);
        startProxy(line);
        startPPP(line);
        return line;
    }

    private void startPPP(Line line) {
        if (line.getOutIpAddr() == null) {
            String ip = dialupPPPoE(line);
            line.setOutIpAddr(ip);
        }
    }

    private void startProxy(Line line) {
        if (!line.isProxyOn()) {
            proxyService.startProxy(line.getLineId(), line.getProxyListenIp(), line.getProxyNamespaceName());
            boolean proxyStart = proxyService.isProxyStart(line.getProxyListenIp(), line.getProxyNamespaceName());
            line.setProxyOn(proxyStart);
        }
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
            line = lineFactory.createLine(lineId);
        }
        lineMap.put(lineId, line);
        boolean proxyStart = proxyService.isProxyStart(line.getProxyListenIp(), line.getProxyNamespaceName());
        line.setProxyOn(proxyStart);
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
