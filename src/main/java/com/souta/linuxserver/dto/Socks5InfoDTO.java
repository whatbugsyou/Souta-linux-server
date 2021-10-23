package com.souta.linuxserver.dto;

import com.souta.linuxserver.util.StringUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Data
@NoArgsConstructor
public class Socks5InfoDTO {
    private String ip;
    private Integer port;
    private List<AuthInfo> authList;
    private Boolean status;

    public Socks5InfoDTO(String ip) {
        this.ip = ip;
        this.port = 18000;
        this.status = false;
        AuthInfo authInfo = new AuthInfo();
        authInfo.setUsername(StringUtil.getRandomString(8));
        authInfo.setPassword(StringUtil.getRandomString(8));
        this.authList = Arrays.asList(authInfo);
    }


}
