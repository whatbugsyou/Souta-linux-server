package com.souta.linuxserver.controller;

import com.souta.linuxserver.dto.Socks5Info;
import com.souta.linuxserver.service.Socks5Service;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1.0/line/socks5")
public class Socks5Controller {
    private final Socks5Service socks5Service;

    public Socks5Controller(Socks5Service socks5Service) {
        this.socks5Service = socks5Service;
    }

    @GetMapping("info")
    public Object getSocks5Info(@RequestParam(value = "ip", defaultValue = "") String ip) {
        if (ip == "") {
            return socks5Service.getAllSocks5();
        }else {
            return socks5Service.getSocks5(ip);
        }
    }

    @PutMapping("/config")
    public void changeConfig(@RequestParam("socks5Config") Socks5Info socks5Info) {
        socks5Service.updateConfig(socks5Info);
    }


}
