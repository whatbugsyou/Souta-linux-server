package com.souta.linuxserver.entity;

import lombok.Data;

@Data
public class PPPOE {
    private String id;
    private String outIP;
    private String gateWay;
    private String adslUser;
    private String adslPassword;
    private String runingOnInterfaceName;
    private String namespaceName;
    private String ethName;

}
