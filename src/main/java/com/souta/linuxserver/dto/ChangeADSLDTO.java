package com.souta.linuxserver.dto;

import lombok.Data;

@Data
public class ChangeADSLDTO {
    private String oldAdslUser;
    private String adslUser;
    private String adslPassword;
    private String ethernetName;
}
