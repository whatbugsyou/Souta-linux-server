package com.souta.linuxserver.entity;

import lombok.*;

@Data
public class Namespace {

    public static Namespace DEFAULT_NAMESPACE = new Namespace(null);

    public static final String DEFAULT_PREFIX = "ns";

    private String name;

    public Namespace(String name) {
        this.name = name;
    }

    public boolean isDefaultNamespace(){
        return this.name == DEFAULT_NAMESPACE.getName();
    }

}
