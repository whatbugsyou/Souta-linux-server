package com.souta.linuxserver.controller;

import com.souta.linuxserver.dto.BatchedChangeADSLDTO;
import com.souta.linuxserver.dto.ChangeOneADSLDTO;
import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.adsl.ADSLConfigManager;
import com.souta.linuxserver.service.PPPOEService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1.0/line/adsl")
public class ADSLController {
    private final PPPOEService pppoeService;
    private final ADSLConfigManager adslConfigManager;

    public ADSLController(PPPOEService pppoeService, ADSLConfigManager adslConfigManager) {
        this.pppoeService = pppoeService;
        this.adslConfigManager = adslConfigManager;
    }

    @PutMapping
    public boolean changeAdslAccount(@RequestBody ChangeOneADSLDTO adsl) {
        ADSL newADSL = new ADSL(adsl.getAdslUsername(), adsl.getAdslPassword(), adsl.getEthernetName());
        return adslConfigManager.changeADSLAccount(adsl.getLineId().intValue() - 1, newADSL);
    }

    @PutMapping("/batched")
    public boolean batchedChangeADSL(@RequestBody BatchedChangeADSLDTO adsl) {
        ADSL newADSL = new ADSL(adsl.getAdslUsername(), adsl.getAdslPassword(), adsl.getEthernetName());
        return adslConfigManager.changeADSLAccount(adsl.getOldAdslUsername(), newADSL);
    }

}
