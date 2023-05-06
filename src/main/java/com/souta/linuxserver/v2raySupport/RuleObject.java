package com.souta.linuxserver.v2raySupport;

import lombok.Data;

@Data
public class RuleObject {
    private String[] inboundTag;
    private String outboundTag;
    private String type = "field";
}
