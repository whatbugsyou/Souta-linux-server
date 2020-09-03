package com.souta.linuxserver.controller;


import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.service.ShadowsocksService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class ShadowsocksController {

    private final ShadowsocksService shadowsocksService;

    public ShadowsocksController(ShadowsocksService shadowsocksService) {
        this.shadowsocksService = shadowsocksService;
    }

    @RequestMapping("/getAllListendShadowsocks.do")
    public List<Shadowsocks> getIpList() {
        return shadowsocksService.getAllListenedShadowsocks();
    }

    @RequestMapping("/test.do")
    public String test() {
        return "test";
    }
}
