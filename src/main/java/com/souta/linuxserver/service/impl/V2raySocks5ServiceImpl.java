package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.prototype.SocksPrototype;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.proxy.ProxyConfig;
import com.souta.linuxserver.service.NamespaceCommandService;
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

    private final ProxyConfig proxyConfig;

    public V2raySocks5ServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService, NamespaceCommandService commandService, ProxyConfig proxyConfig) {
        super(namespaceService, pppoeService, commandService, proxyConfig.getSocks5Config().getPort());
        this.proxyConfig = proxyConfig;
    }

    @PostConstruct
    public void init() {
        initPrototype();
    }

    private void initPrototype() {
        SocksPrototype socks = new Socks5(proxyConfig.getSocks5Config().getUsername(), proxyConfig.getSocks5Config().getPassword());
        socks.setPort(proxyConfig.getSocks5Config().getPort().toString());
        SocksPrototypeManager.registerType(socks);
    }


    @Override
    public boolean checkConfigFileExist(String id) {
        File file = new File(configFileDir, "v2ray-" + id + ".json");
        return file.exists();
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
        try (FileWriter fileWriter = new FileWriter(file);
             BufferedWriter cfgfileBufferedWriter = new BufferedWriter(fileWriter);
             InputStream v2rayConfigStream = this.getClass().getResourceAsStream("/static/v2rayConfig.json");
             InputStreamReader inputStreamReader = new InputStreamReader(v2rayConfigStream);
             BufferedReader tmpbufferedReader = new BufferedReader(inputStreamReader)) {

            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace("{PORT}", listenPort.toString());
                line = line.replace("{USERNAME}", proxyConfig.getSocks5Config(id).getPassword());
                line = line.replace("{PASSWORD}", proxyConfig.getSocks5Config(id).getPassword());
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean startSocks(String id, String ip) {
        if (createConfigFile(id, ip)) {
            String namespaceName = Namespace.DEFAULT_PREFIX + id;
            String cmd = "v2ray run -c /root/v2ray/v2ray-" + id + ".json >/dev/null 2>&1 &";
            commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
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
        Process process = commandService.exec(cmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    result.add(matcher.group(1));
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Socks5 getSocksInstance() {
        return (Socks5) SocksPrototypeManager.getProtoType(Socks5.class);
    }
}
