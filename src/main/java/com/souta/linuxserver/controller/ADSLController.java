package com.souta.linuxserver.controller;

import com.souta.linuxserver.dto.ChangeADSLDTO;
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
    public boolean changeAdslAccount(@RequestParam("lineId") String lineId, @RequestBody ADSL adsl) {
        return pppoeService.changeADSLAccount(lineId, adsl);
    }

    @PutMapping("/batched")
    public boolean batchedChangeADSL(@RequestBody ChangeADSLDTO adsl) {
        return pppoeService.batchedChangeADSLAccount(adsl);
    }

}
