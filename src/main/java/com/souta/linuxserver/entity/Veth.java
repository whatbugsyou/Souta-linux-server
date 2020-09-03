package com.souta.linuxserver.entity;

import lombok.*;

import java.io.IOException;

@Data
public class Veth {
    private String physicalEthName; // Physical ethernet
    private String interfaceName ; // vertual ethernet
    private String macAddr;
    private Namespace namespace;

    public Veth(String physicalEthName, String interfaceName, String macAddr, Namespace namespace) {
        this.physicalEthName = physicalEthName;
        this.interfaceName = interfaceName;
        this.macAddr = macAddr;
        this.namespace = namespace;
    }

    public Veth(String physicalEthName) {
        this.physicalEthName = physicalEthName;
        this.interfaceName = interfaceName;
        this.macAddr = macAddr;
        this.namespace = null;
    }

    public Veth() {
    }
}
