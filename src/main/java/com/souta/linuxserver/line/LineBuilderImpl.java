package com.souta.linuxserver.line;

import com.souta.linuxserver.proxy.ProxyService;
import com.souta.linuxserver.service.PPPOEService;
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
    private final LineFactory lineFactory;
    private final ProxyService proxyService;

    public LineBuilderImpl(PPPOEService pppoeService, LineFactory lineFactory, ProxyService proxyService) {
        this.pppoeService = pppoeService;
        this.lineFactory = lineFactory;
        this.proxyService = proxyService;
    }


    @Override
    public Line build(String lineId) {
        Line line = getLine(lineId);
        if (!proxyService.isProxyStart(lineId)) {
            proxyService.startProxy(lineId);
        }
        if (line.getOutIpAddr() == null) {
            String ip = dialupPPPoE(line);
            line.setOutIpAddr(ip);
        }
        return line;
    }

    @Override
    public Line getLine(String lineId) {
        Line line = lineMap.get(lineId);
        if (line == null) {
            line = lineFactory.getLine(lineId);
        }
        lineMap.put(lineId, line);
        String ip = pppoeService.getIP(lineId);
        line.setOutIpAddr(ip);
        return line;
    }

    public String dialupPPPoE(Line line) {
        return pppoeService.dialUp(line.getLineId(), line.getAdsl().getAdslUser(), line.getAdsl().getAdslPassword(), line.getVethName(), line.getNamespaceName());
    }
}
