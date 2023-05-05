package com.souta.linuxserver.v2raySupport.shadowsocks;

import com.souta.linuxserver.v2raySupport.Settings;
import lombok.Data;

@Data
public class ShadowsocksSettings extends Settings {
    private String method;
    private String password;
    private int userLevel;
    private boolean ota;
    private String network;
}
