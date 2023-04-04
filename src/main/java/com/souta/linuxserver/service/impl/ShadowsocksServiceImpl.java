package com.souta.linuxserver.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.config.LineConfig;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.abs.AbstractShadowsocksService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Service
public class ShadowsocksServiceImpl extends AbstractShadowsocksService {

    private static String configFileDir = "/root/shadowsocks";

    public ShadowsocksServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService, LineConfig lineConfig) {
        super(namespaceService, pppoeService, lineConfig.getShadowsocksConfig().getPort(), lineConfig.getShadowsocksConfig());
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        File file = new File(configFileDir, String.format("shadowsocks-%s.json", id));
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
        File file = new File(dir, String.format("shadowsocks-%s.json", id));
        FileWriter fileWriter = null;
        try {
            JSONObject shadowsocksConfigObj = new JSONObject();
            shadowsocksConfigObj.put("server", ip);
            shadowsocksConfigObj.put("server_port", config.getPort());
            shadowsocksConfigObj.put("local_address", "127.0.0.1");
            shadowsocksConfigObj.put("local_port", "1080");
            shadowsocksConfigObj.put("password", config.getPassword());
            shadowsocksConfigObj.put("timeout", 600);
            shadowsocksConfigObj.put("method", config.getMethod());
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
            namespaceService.exeCmdWithNewSh(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

}
