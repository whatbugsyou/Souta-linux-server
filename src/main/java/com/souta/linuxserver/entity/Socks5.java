package com.souta.linuxserver.entity;

import com.souta.linuxserver.entity.prototype.SocksPrototype;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
public class Socks5 extends SocksPrototype {

    private String username;
    private String password;
}


