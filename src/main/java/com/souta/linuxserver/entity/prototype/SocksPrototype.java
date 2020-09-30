package com.souta.linuxserver.entity.prototype;

public abstract class SocksPrototype implements Cloneable{
    @Override
    protected SocksPrototype clone()  {
        Object clone = null;
        try {
            clone = super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return (SocksPrototype)clone;
    }
}
