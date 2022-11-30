package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {
    private final static String UPTO_PLACEHOLDER = "{UPTO_PLACEHOLDER}";
    private final static String BURST_PLACEHOLDER = "{BURST_PLACEHOLDER}";
    private final static String TAG_PLACEHOLDER = "{TAG}";
    private final static String configFileDir = "/root/limitScript";
    private final NamespaceService namespaceService;
    private final HostConfig hostConfig;

    public RateLimitServiceImpl(NamespaceService namespaceService, HostConfig hostConfig) {
        this.namespaceService = namespaceService;
        this.hostConfig = hostConfig;
    }

    public static String getNumeric(String str) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        return m.replaceAll("").trim();
    }

    @Override
    public boolean limit(String lineId, Integer maxKBPerSec) {
        log.info("rate limit:line{}, {}kb/s ", lineId, maxKBPerSec);
        createLimitScriptFile(lineId, maxKBPerSec);
        namespaceService.exeCmdInNamespace(Namespace.DEFAULT_PREFIX + lineId, "iptables-restore " + configFileDir + "/" + "limit-line" + lineId + "-" + maxKBPerSec + "kb.conf");
        return true;
    }

    @Override
    public boolean limit(String lineId) {
        return limit(lineId, hostConfig.getHost().getRateLimitKB());
    }

    @Override
    public void removeAll() {
        String cmd = "ip -all netns exec iptables --flush";
        namespaceService.exeCmdInDefaultNamespace(cmd);
    }

    @Override
    public Set<String> getLimitedLineIdSet() {
        HashSet<String> result = new HashSet<>();
        String cmd = "ip -all netns exec iptables -L RATE-LIMIT";

        try (InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd); InputStreamReader inputStreamReader = new InputStreamReader(inputStream); BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            String line = null;
            String namespaceId = null;
            Boolean ACCETP_EXIST = false;
            Boolean DROP_EXIST = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(Namespace.DEFAULT_PREFIX)) {
                    namespaceId = getNumeric(line);
                    ACCETP_EXIST = false;
                    DROP_EXIST = false;
                }
                if (line.contains("ACCEPT") && line.contains(hostConfig.getHost().getRateLimitKB() + "kb/s")) {
                    ACCETP_EXIST = true;
                }
                if (line.contains("DROP")) {
                    DROP_EXIST = true;
                }
                if (ACCETP_EXIST && DROP_EXIST) {
                    result.add(namespaceId);
                }
            }
        } catch (Exception e) {
            log.error("getLimitedLineIdSet:{},{}", e.getMessage(), e.getStackTrace());
        }
        return result;
    }

    private void createLimitScriptFile(String lineId, Integer maxKBPerSec) {
        File dir = new File(configFileDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, "limit-line" + lineId + "-" + maxKBPerSec + "kb.conf");
        try (FileWriter fileWriter = new FileWriter(file); BufferedWriter cfgfileBufferedWriter = new BufferedWriter(fileWriter); InputStream v2rayConfigStream = this.getClass().getResourceAsStream("/static/RateLimit.conf"); InputStreamReader inputStreamReader = new InputStreamReader(v2rayConfigStream); BufferedReader tmpbufferedReader = new BufferedReader(inputStreamReader)) {
            String line;
            Integer packagePerSec = maxKBPerSec;
            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace(UPTO_PLACEHOLDER, packagePerSec + "kb/sec");
                line = line.replace(BURST_PLACEHOLDER, packagePerSec.toString() + "kb");
                line = line.replace(TAG_PLACEHOLDER, lineId);
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
            cfgfileBufferedWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
