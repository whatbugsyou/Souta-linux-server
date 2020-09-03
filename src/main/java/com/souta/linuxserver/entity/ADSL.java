package com.souta.linuxserver.entity;

import lombok.Data;

@Data
public class ADSL {
    private String adslUser;
    private String adslPassword;
    public ADSL(String adslUser, String adslPassword) {
        this.adslUser = adslUser;
        this.adslPassword = adslPassword;
    }

}
