package com.souta.linuxserver.entity.prototype;

import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.abs.Socks;

import java.util.HashMap;

public class SocksPrototypeManager {
    private static final HashMap<Class<? extends Socks>, SocksPrototype> pool = new HashMap<>();

    static {
        Shadowsocks shadowsocks = new Shadowsocks();
        pool.put(Shadowsocks.class, shadowsocks);
        Socks5 socks5 = new Socks5();
        pool.put(Socks5.class, socks5);
    }

    public static SocksPrototype getProtoType(Class<? extends Socks> clazz) {
        SocksPrototype temp = pool.get(clazz);
        return temp.clone();
    }

}
