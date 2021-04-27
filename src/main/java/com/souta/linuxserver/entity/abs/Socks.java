package com.souta.linuxserver.entity.abs;

import lombok.Data;

import java.util.Objects;

@Data
public abstract class Socks {
    public String id;
    public String ip;
    public String port;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Socks that = (Socks) o;
        return Objects.equals(ip, that.ip) &&
                id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, id);
    }
}
