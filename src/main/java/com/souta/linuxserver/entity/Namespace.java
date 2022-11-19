package com.souta.linuxserver.entity;

import lombok.*;

@Data
public class Namespace {

    public static final String DEFAULT_PREFIX = "ns";

    private String name;

    public Namespace(String name) {
        this.name = name;
    }

}
