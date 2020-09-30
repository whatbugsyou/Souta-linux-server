package com.souta.linuxserver.entity.prototype;

import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.Socks5;

import java.util.HashMap;

public class SocksPrototypeManager {
    private static final HashMap<String,SocksPrototype> pool = new HashMap<>();
    static {
        Shadowsocks shadowsocks = new Shadowsocks();
        shadowsocks.setPassword("test123");
        shadowsocks.setPort("10809");
        shadowsocks.setEncryption("aes-256-cfb");
        pool.put("Shadowsocks",shadowsocks);

        Socks5 socks5 = new Socks5();
        socks5.setUsername("test123");
        socks5.setPassword("test123");
        socks5.setPort("10808");
        pool.put("Socks5",socks5);

    }
    public static SocksPrototype getProtoType(String key){
        SocksPrototype temp=pool.get(key);
        return temp.clone();
    }

}
