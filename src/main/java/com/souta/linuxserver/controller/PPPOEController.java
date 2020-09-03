package com.souta.linuxserver.controller;

import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.service.PPPOEService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pppoe/")
public class PPPOEController {
    private final PPPOEService pppoeService;

    private static final Logger log = LoggerFactory.getLogger(PPPOEController.class);

    public PPPOEController(PPPOEService pppoeService) {
        this.pppoeService = pppoeService;
    }

    @RequestMapping("get.do")
    public PPPOE getpppoe(String id) {
        return pppoeService.getPPPOE(id);
    }

    @RequestMapping("create.do")
    public PPPOE createpppoe(String id) {
        return pppoeService.createPPPOE(id, null);
    }

    @RequestMapping("refreshIp.do")
    public String refreshIp(String id) {
        pppoeService.reDialup(id);
        return "ok";
    }

    @RequestMapping("dialUp.do")
    public String dialUp(String id) {
        pppoeService.dialUp(id);
        return "ok";
    }

    @DeleteMapping
    public String shutDown(String id) {
        pppoeService.shutDown(id);
        return "ok";
    }


}
