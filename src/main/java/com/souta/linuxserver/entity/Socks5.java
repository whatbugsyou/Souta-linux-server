package com.souta.linuxserver.entity;

import lombok.Data;

@Data
public class Socks5 {
    private String id;
    private String ip;
    private String port;
    private String username;
    private String password;
    private String pid;


    public Socks5() {
    }
}
