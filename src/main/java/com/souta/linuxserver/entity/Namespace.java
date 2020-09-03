package com.souta.linuxserver.entity;

import lombok.*;

@Data
public class Namespace {
    private String name;

    public Namespace(String name) {
        this.name = name;
    }

}
