package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.config.LineConfig;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.prototype.SocksPrototype;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.Socks5Service;
import com.souta.linuxserver.service.abs.AbstractSocksService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service()
public class V2raySocks5ServiceImpl extends AbstractSocksService<Socks5> implements Socks5Service {

    private static final String configFileDir = "/root/v2ray";

    private final LineConfig lineConfig;

    public V2raySocks5ServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService, LineConfig lineConfig) {
        super(namespaceService, pppoeService, lineConfig.getSocks5Config().getPort());
        this.lineConfig = lineConfig;
    }

    @PostConstruct
    public void init() {
        initPrototype();
    }

    private void initPrototype() {
        SocksPrototype socks = new Socks5(lineConfig.getSocks5Config().getUsername(), lineConfig.getSocks5Config().getUsername());
        socks.setPort(lineConfig.getSocks5Config().getPort().toString());
        SocksPrototypeManager.add(socks);
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
                line = line.replace("{PORT}", listenPort.toString());
                line = line.replace("{USERNAME}", lineConfig.getSocks5Config().getUsername());
                line = line.replace("{PASSWORD}", lineConfig.getSocks5Config().getPassword());
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
            String namespaceName = Namespace.DEFAULT_PREFIX + id;
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
