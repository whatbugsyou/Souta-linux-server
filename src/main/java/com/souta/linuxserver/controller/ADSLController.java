package com.souta.linuxserver.controller;

import com.souta.linuxserver.dto.BatchedChangeADSLDTO;
import com.souta.linuxserver.dto.ChangeOneADSLDTO;
import com.souta.linuxserver.entity.ADSL;
import com.souta.linuxserver.service.PPPOEService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1.0/line/adsl")
public class ADSLController {
    private final PPPOEService pppoeService;

    public ADSLController(PPPOEService pppoeService) {
        this.pppoeService = pppoeService;
    }

    @PutMapping
    public boolean changeAdslAccount(@RequestBody ChangeOneADSLDTO adsl) {
        return pppoeService.changeADSLAccount(adsl);
    }

    @PutMapping("/batched")
    public boolean batchedChangeADSL(@RequestBody BatchedChangeADSLDTO adsl) {
        return pppoeService.batchedChangeADSLAccount(adsl);
    }

}
