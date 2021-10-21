package com.souta.linuxserver.dto;

import lombok.Data;

@Data
public class AuthInfo {
    private String username;
    private String password;
    private Long expireTimestamp;
    private Long startTimestamp;
}
