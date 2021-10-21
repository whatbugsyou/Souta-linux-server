package com.souta.linuxserver.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
        authInfo.setUsername(getRandomString(8));
        authInfo.setPassword(getRandomString(8));
        this.authList = Arrays.asList(authInfo);
    }

    public static String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

}
