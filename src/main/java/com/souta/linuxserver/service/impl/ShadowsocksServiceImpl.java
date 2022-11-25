package com.souta.linuxserver.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.config.LineConfig;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.prototype.SocksPrototype;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import com.souta.linuxserver.service.abs.AbstractSocksService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ShadowsocksServiceImpl extends AbstractSocksService<Shadowsocks> implements ShadowsocksService {

    private static String configFileDir = "/root/shadowsocks";
    private final LineConfig lineConfig;

    public ShadowsocksServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService, LineConfig lineConfig) {
        super(namespaceService, pppoeService, lineConfig.getShadowsocksConfig().getPort());
        this.lineConfig = lineConfig;
        this.socksProtoTypeClass = Shadowsocks.class;
    }

    @PostConstruct
    public void init() {
        initPrototype();
    }

    private void initPrototype() {
        SocksPrototype socks = new Shadowsocks(lineConfig.getShadowsocksConfig().getPassword(), lineConfig.getShadowsocksConfig().getMethod());
        socks.setPort(lineConfig.getShadowsocksConfig().getPort().toString());
        SocksPrototypeManager.add(socks);
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = String.format("ls " + configFileDir + " |grep shadowsocks-%s.json", id);
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
        File file = new File(dir, String.format("shadowsocks-%s.json", id));
        FileWriter fileWriter = null;
        try {
            JSONObject shadowsocksConfigObj = new JSONObject();
            shadowsocksConfigObj.put("server", ip);
            shadowsocksConfigObj.put("server_port", lineConfig.getShadowsocksConfig().getPort());
            shadowsocksConfigObj.put("local_address", "127.0.0.1");
            shadowsocksConfigObj.put("local_port", "1080");
            shadowsocksConfigObj.put("password", lineConfig.getShadowsocksConfig().getPassword());
            shadowsocksConfigObj.put("timeout", 600);
            shadowsocksConfigObj.put("method", lineConfig.getShadowsocksConfig().getMethod());
            fileWriter = new FileWriter(file);
            fileWriter.write(shadowsocksConfigObj.toJSONString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
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
            String cmd = "ssserver -c " + configFileDir + "/shadowsocks-%s.json >/dev/null 2>&1 &";
            cmd = String.format(cmd, id, id);
            String namespaceName = Namespace.DEFAULT_PREFIX + id;
            namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

}
