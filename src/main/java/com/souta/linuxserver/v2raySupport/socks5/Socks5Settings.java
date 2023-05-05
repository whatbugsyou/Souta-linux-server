package com.souta.linuxserver.v2raySupport.socks5;

import com.souta.linuxserver.v2raySupport.Settings;
import lombok.Data;

@Data
public class Socks5Settings extends Settings {
    private String auth;
    private String ip;
    private boolean udp;
    private int userLevel;
    private AccountObject[] accountObject;
}
