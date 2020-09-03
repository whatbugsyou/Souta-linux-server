package com.souta.linuxserver.entity;

import lombok.*;

@Data
public class PPPOE {
    private Veth veth;
    private String id;
    private String outIP;
    private String gateWay;
    private String adslUser;
    private String adslPassword;
    private String runingOnInterfaceName;

    public PPPOE() {

    }

    public PPPOE(String id) {
        this.id = id;
    }

    public PPPOE(Veth veth, String id, String adslUser, String adslPassword) {
        this.veth = veth;
        this.id = id;
        this.adslUser = adslUser;
        this.adslPassword = adslPassword;
    }

    public PPPOE(Veth veth, String id, String outIP, String gateWay, String adslUser, String adslPassword, String runingOnInterfaceName) {
        this.veth = veth;
        this.id = id;
        this.outIP = outIP;
        this.gateWay = gateWay;
        this.adslUser = adslUser;
        this.adslPassword = adslPassword;
        this.runingOnInterfaceName = runingOnInterfaceName;
    }

}
