package com.souta.linuxserver.controller;

import com.souta.linuxserver.service.HostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1.0/host")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }


    @PutMapping("rateLimit/{limitKB}")
    public void updateRateLimit(@PathVariable(value = "limitKB") Integer limitKB) {
        hostService.updateRateLimit(limitKB);
    }
}