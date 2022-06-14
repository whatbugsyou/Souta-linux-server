package com.souta.linuxserver.dto;

import lombok.Data;

@Data
public class BatchedChangeADSLDTO {
    private Long hostId;
    private String oldAdslUsername;
    private String adslUsername;
    private String adslPassword;
    private String ethernetName;
}
