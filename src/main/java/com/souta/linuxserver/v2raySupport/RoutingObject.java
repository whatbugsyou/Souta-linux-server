package com.souta.linuxserver.v2raySupport;

import lombok.Data;

import java.util.List;

@Data
public class RoutingObject {
    private List<RuleObject> rules;
}
