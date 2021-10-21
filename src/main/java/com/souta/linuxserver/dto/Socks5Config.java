package com.souta.linuxserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class Socks5Config {
    private String ip;
    private Integer port;
    private List<authInfo> authList;

    @Data
    private class authInfo {
        private String username;
        private String password;
        private Long expireTimestamp;
        private Long startTimestamp;
    }

}
