package com.souta.linuxserver.entity;

import lombok.*;

@Data
public class Shadowsocks {
    private String ip;
    private String port;
    private String password;
    private String encryption;
    private String pid;
    private String ownerId;
    private String id;


    public Shadowsocks() {
    }

    public Shadowsocks(String ip, String port, String password, String encryption, String pid, String owner) {
        this.ip = ip;
        this.port = port;
        this.password = password;
        this.encryption = encryption;
        this.pid = pid;
        this.ownerId = owner;
    }

}
