package com.souta.linuxserver.line;

import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.entity.abs.Socks;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Line {
    private String lineId;
    private String namespaceName;
    private String vethName;
    private ADSL adsl;
    private String outIpAddr;
    private List<Socks> proxyServers;
    private String proxyNamespaceName;
    private String proxyListenIp;
    private boolean proxyOn;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Line line = (Line) o;
        return Objects.equals(lineId, line.getLineId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineId);
    }
}