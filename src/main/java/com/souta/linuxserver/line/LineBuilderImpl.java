package com.souta.linuxserver.line;

import com.souta.linuxserver.line.environment.LineEnvironmentBuilder;
import com.souta.linuxserver.proxy.ProxyService;
import com.souta.linuxserver.service.PPPOEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component("lineBuilder")
@Slf4j
public class LineBuilderImpl implements LineBuilder {
    private static final ConcurrentHashMap<String, Line> lineMap = new ConcurrentHashMap<>();
    private final PPPOEService pppoeService;
    private final LineFactory lineFactory;
    private final LineEnvironmentBuilder lineEnvironmentBuilder;
    private final ProxyService proxyService;

    public LineBuilderImpl(PPPOEService pppoeService, LineFactory lineFactory, LineEnvironmentBuilder lineEnvironmentBuilder, ProxyService proxyService) {
        this.pppoeService = pppoeService;
        this.lineFactory = lineFactory;
        this.lineEnvironmentBuilder = lineEnvironmentBuilder;
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
            if (lineEnvironmentBuilder.build(lineId)) {
                line = lineFactory.createLine(lineId);
            } else {
                return null;
            }
        }
        lineMap.put(lineId, line);
        boolean proxyStart = proxyService.isProxyStart(line.getProxyListenIp(), line.getProxyNamespaceName());
        line.setProxyOn(proxyStart);
        String ip = pppoeService.getIP(lineId);
        line.setOutIpAddr(ip);
        return line;
    }


    public String dialupPPPoE(Line line) {
        return pppoeService.dialUp(line.getLineId(), line.getAdsl().getAdslUser(), line.getAdsl().getAdslPassword(), line.getVethName(), line.getNamespaceName());
    }
}
