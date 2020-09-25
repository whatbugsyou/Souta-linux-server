package com.souta.linuxserver.entity;

import lombok.Data;

@Data
public class LineStatus {
    private String id;
    private int status;

    public LineStatus(String id, int status) {
        this.id = id;
        this.status = status;
    }

    public LineStatus(String id) {
        this.id = id;
    }
}
