package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.Data;

@Data
public class Shadowsocks extends SocksPrototype {
    public static final String DEFAULT_ENCRYPTION="rc4_md5";//aes_256_gcm
    public static final String  DEFAULT_PASSWORD = "test123";
    public static final String DEFAULT_PORT ="10809";
    private String password;
    private String encryption;
    public Shadowsocks() {
        setPassword(DEFAULT_PASSWORD);
        setPort(DEFAULT_PORT);
        setEncryption(DEFAULT_ENCRYPTION);
    }
}
