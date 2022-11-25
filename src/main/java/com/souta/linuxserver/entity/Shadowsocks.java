package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Shadowsocks extends SocksPrototype {

    private String password;
    private String encryption;

}
