package com.souta.linuxserver.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class Socks5Info {
    private String ip;
    private Integer port;
    private List<authInfo> authList;
    private Boolean status;

    public Socks5Info(String ip) {
        this.ip = ip;
        this.port = 18000;
        this.status = false;
        authInfo authInfo = new authInfo();
        authInfo.setUsername("test");
        authInfo.setPassword("test");
        this.authList = Arrays.asList(authInfo);
    }

    @Data
    public class authInfo {
        private String username;
        private String password;
        private Long expireTimestamp;
        private Long startTimestamp;
    }

}
