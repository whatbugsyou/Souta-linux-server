package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.RateLimitService;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class RateLimitServiceImpl implements RateLimitService {
    private final NamespaceService namespaceService;

    private final static String UPTO_PLACEHOLDER = "{UPTO_PLACEHOLDER}";

    private final static String BURST_PLACEHOLDER = "{BURST_PLACEHOLDER}";

    private final static String TAG_PLACEHOLDER = "{TAG}";

    private final static String configFileDir = "~/limitScript";

    public RateLimitServiceImpl(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @Override
    public boolean limit(String lineId, Integer maxKBPerSec) {
        createLimitScriptFile(lineId, maxKBPerSec);
        namespaceService.exeCmdInNamespace("ns" + lineId, "sh " + configFileDir + "/" + "limit-" + maxKBPerSec + "kb/s.sh");
        return true;
    }

    private void createLimitScriptFile(String lineId, Integer maxKBPerSec) {
        File dir = new File(configFileDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, "rate-" + maxKBPerSec + "KB.sh");
        if (file.exists()) {
            return;
        }
        try (FileWriter fileWriter = new FileWriter(file); BufferedWriter cfgfileBufferedWriter = new BufferedWriter(fileWriter); InputStream v2rayConfigStream = this.getClass().getResourceAsStream("/static/RateLimit.sh"); InputStreamReader inputStreamReader = new InputStreamReader(v2rayConfigStream); BufferedReader tmpbufferedReader = new BufferedReader(inputStreamReader);) {
            String line;
            Integer packagePerSec = maxKBPerSec;
            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace(UPTO_PLACEHOLDER, packagePerSec + "kb/sec");
                line = line.replace(BURST_PLACEHOLDER, packagePerSec.toString() + "kb");
                line = line.replace(TAG_PLACEHOLDER, lineId);
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
