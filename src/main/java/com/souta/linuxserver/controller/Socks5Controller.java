package com.souta.linuxserver.controller;

import com.souta.linuxserver.dto.Socks5Config;
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
            //get all ip and socks5 info
        }else {
//            socks5Service.getSocks(ip);
        }
        return null;
    }

    @PutMapping("status")
    public Object changeStatus(@RequestParam("ip")String ip, @RequestParam("status")Boolean status) {
        if (status) {
//            socks5Service.startSocks(ip);
        }else {
//            socks5Service.stopSocks(ip);
        }
        return null;
    }

    @PostMapping("/config/auth")
    public Object addAuth(@RequestParam("socks5Config") Socks5Config socks5Config) {
        return null;
    }

    @DeleteMapping("/config/auth")
    public Object deleteAuth(@RequestParam("socks5Config") Socks5Config socks5Config) {
        return null;
    }

    @PutMapping("/config/auth")
    public Object changeAuth(@RequestParam("socks5Config") Socks5Config socks5Config) {
        return null;
    }
}
