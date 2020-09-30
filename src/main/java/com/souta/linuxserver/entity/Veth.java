package com.souta.linuxserver.entity;

import lombok.Data;

@Data
public class Veth {
    private String physicalEthName; // Physical ethernet
    private String interfaceName; // virtual ethernet
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
