package com.souta.linuxserver.controller;

import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.service.Socks5Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/socket5")
public class Socks5Controller {
    @Autowired
    private Socks5Service socks5Service;
    private static final Logger log = LoggerFactory.getLogger(Socks5Controller.class);
    @RequestMapping("getAllListendShadowsocks.do")
    public List<Socks5> getIpList(){
        return  socks5Service.getAllListenedSocks5();
    }
    @RequestMapping("test.do")
    public String test(){
        return "test";
    }
    @RequestMapping("create")
    public Socks5 create(String id){
        socks5Service.createSocks5ConfigFile(id);
        socks5Service.startSocks5(id);
        return socks5Service.getSocks5(id);
    }

}
