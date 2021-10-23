package com.souta.linuxserver.dto;

import lombok.Data;

@Data
public class Line {
    private String hostIp;
    private Socks5InfoDTO socks5Info;
}
