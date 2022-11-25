package com.souta.linuxserver.entity.prototype;

import com.souta.linuxserver.entity.abs.Socks;

import java.util.HashMap;

public class SocksPrototypeManager {
    private static final HashMap<Class<? extends Socks>, SocksPrototype> pool = new HashMap<>();

    public static void add(SocksPrototype socksProtoType) {
        pool.put(socksProtoType.getClass(), socksProtoType);
    }

    public static SocksPrototype getProtoType(Class<? extends Socks> clazz) {
        SocksPrototype temp = pool.get(clazz);
        return temp.clone();
    }

}
