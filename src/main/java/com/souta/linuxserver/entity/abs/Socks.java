package com.souta.linuxserver.entity.abs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Socks {
    private String id;
    private String ip;
    private String port;

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
