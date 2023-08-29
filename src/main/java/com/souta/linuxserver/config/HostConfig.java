package com.souta.linuxserver.config;

import com.alibaba.fastjson.JSON;
import com.souta.linuxserver.util.FileUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;

@Data
@ConfigurationProperties(prefix = "host")
@Slf4j
public class HostConfig {
    private String javaServerHost;
    private String filePath;
    private Integer defaultRateLimitKB;
    private String coreVersion;
    private Host host;

    @PostConstruct
    public void init() {
        try {
            readHostFromConfigFile();
            if (host.rateLimitKB == null) {
                host.rateLimitKB = defaultRateLimitKB;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            System.exit(1);
        }
    }

    private void readHostFromConfigFile() throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException(filePath);
        }
        String jsonStr = FileUtil.ReadFile(filePath);
        host = JSON.parseObject(jsonStr, Host.class);
    }

    @Data
    public static class Host {
        private String id;
        private String ip;
        private String name;
        private String location;
        private String port;
        private String operator;
        private Integer version;
        private Integer limit;
        private Integer rateLimitKB;
        private String coreVersion;
    }
}
