package com.souta.linuxserver.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
public class Socks5Info {
    private String ip;
    private Integer port;
    private List<AuthInfo> authList;
    private Boolean status;

    public Socks5Info(String ip) {
        this.ip = ip;
        this.port = 18000;
        this.status = false;
        AuthInfo authInfo = new AuthInfo();
        authInfo.setUsername("test");
        authInfo.setPassword("test");
        this.authList = Arrays.asList(authInfo);
    }


}
