package com.souta.linuxserver.entity.prototype;

import com.souta.linuxserver.entity.abs.Socks;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class SocksPrototype extends Socks implements Cloneable {

    @Override
    protected SocksPrototype clone() {
        Object clone = null;
        try {
            clone = super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return (SocksPrototype) clone;
    }


}
