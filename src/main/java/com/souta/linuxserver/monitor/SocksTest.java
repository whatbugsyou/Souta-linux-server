package com.souta.linuxserver.monitor;

import com.souta.linuxserver.entity.abs.Socks;

public interface SocksTest {
    boolean isOK(Socks socks);
}