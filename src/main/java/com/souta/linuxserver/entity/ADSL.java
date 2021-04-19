package com.souta.linuxserver.entity;

import lombok.Data;

@Data
public class ADSL {
    private String adslUser;
    private String adslPassword;
    private String ethernetName;

    public ADSL(String adslUser, String adslPassword, String ethernetName) {
        this.adslUser = adslUser;
        this.adslPassword = adslPassword;
        this.ethernetName = ethernetName;
    }

    @Override
    public String toString() {
        String gap = "----";
        return new StringBuilder().append(adslUser).append(gap).append(adslPassword).append(gap).append(ethernetName).toString();
    }
}
