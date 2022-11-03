package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.Socks5Service;
import com.souta.linuxserver.service.abs.AbstractSocksService;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.souta.linuxserver.entity.Socks5.DEFAULT_PORT;

@Service()
public class V2raySocks5ServiceImpl extends AbstractSocksService implements Socks5Service {

    public V2raySocks5ServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService) {
        super(namespaceService, pppoeService);
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
        this.port = DEFAULT_PORT;
        this.log = LoggerFactory.getLogger(V2raySocks5ServiceImpl.class);
        this.configFileDir = "/root/v2ray";
        this.socksProtoTypeClass = Socks5.class;
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = "ls " + configFileDir + " |grep v2ray-" + id + ".json";
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        return hasOutput(inputStream);
    }

    @Override
    public boolean createConfigFile(String id, String ip) {
        if (ip == null) {
            return false;
        }
        File dir = new File(configFileDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, "v2ray-" + id + ".json");
        String line;
        FileWriter fileWriter = null;
        BufferedWriter cfgfileBufferedWriter = null;
        BufferedReader tmpbufferedReader = null;
        InputStream v2rayConfigStream = null;
        try {
            fileWriter = new FileWriter(file);
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            v2rayConfigStream = this.getClass().getResourceAsStream("/static/v2rayConfig.json");
            InputStreamReader inputStreamReader = new InputStreamReader(v2rayConfigStream);
            tmpbufferedReader = new BufferedReader(inputStreamReader);
            while (((line = tmpbufferedReader.readLine()) != null)) {
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tmpbufferedReader != null) {
                try {
                    tmpbufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (v2rayConfigStream != null) {
                try {
                    tmpbufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (cfgfileBufferedWriter != null) {
                try {
                    cfgfileBufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    @Override
    public boolean startSocks(String id, String ip) {
        if (createConfigFile(id, ip)) {
            String namespaceName = "ns" + id;
            String cmd = "v2ray run -c /root/v2ray/v2ray-" + id + ".json >/dev/null 2>&1 &";
            namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public HashSet<String> getStartedIdSet() {
        HashSet<String> result = new HashSet<>();
        String cmd = " pgrep -a v2ray|awk '/v2ray-[0-9]+\\.json/ {print $5}'";
        Pattern compile = Pattern.compile(".*-(\\d+).*");
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        result.add(matcher.group(1));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
