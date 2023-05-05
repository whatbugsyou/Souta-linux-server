package com.souta.linuxserver.v2raySupport;

import lombok.Data;

@Data
public class InBoundObject {

    private int port;

    private String protocol;

    private Settings settings;

    private String listen;
    private String tag;


//    private StreamSettings streamSettings;
//    private Sniffing sniffing;
//    private Allocate allocate;

}
