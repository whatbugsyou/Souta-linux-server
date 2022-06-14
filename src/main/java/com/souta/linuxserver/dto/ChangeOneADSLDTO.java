package com.souta.linuxserver.dto;

import lombok.Data;

@Data
public class ChangeOneADSLDTO {
    private Long hostId;
    private Long lineId;
    private String adslUsername;
    private String adslPassword;
    private String ethernetName;
}
