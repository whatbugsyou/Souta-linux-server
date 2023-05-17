package com.souta.linuxserver.v2raySupport.freedom;

import com.souta.linuxserver.v2raySupport.Settings;
import lombok.Data;

@Data
public class FreedomSettings extends Settings {
    private String domainStrategy;
    private String redirect;
}
