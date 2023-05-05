package com.souta.linuxserver.v2raySupport;

import lombok.Data;

@Data
public class OutBoundObject {
    private String sendThrough;
    private String tag;
    private String protocol;
    private Settings settings;
}
