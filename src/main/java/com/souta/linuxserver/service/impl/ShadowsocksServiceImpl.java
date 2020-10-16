package com.souta.linuxserver.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.service.abs.AbstractSocksService;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.souta.linuxserver.entity.Shadowsocks.*;

@Service
public class ShadowsocksServiceImpl extends AbstractSocksService implements ShadowsocksService{

    public ShadowsocksServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService) {
        super(namespaceService, pppoeService);
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
        this.port = DEFAULT_PORT;
        this.log = LoggerFactory.getLogger(ShadowsocksService.class);
        this.configFileDir = "/root/shadowsocks";
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = String.format("ls "+configFileDir+" |grep shadowsocks-%s.json", id);
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
            shadowsocksConfigObj.put("server",ip);
            shadowsocksConfigObj.put("server_port",Integer.parseInt(port));
            shadowsocksConfigObj.put("local_address","127.0.0.1");
            shadowsocksConfigObj.put("local_port","1080");
            shadowsocksConfigObj.put("password",DEFAULT_PASSWORD);
            shadowsocksConfigObj.put("timeout",600);
            shadowsocksConfigObj.put("method",DEFAULT_ENCRYPTION);
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
            String cmd = "ssserver -c "+configFileDir+"/shadowsocks-%s.json >/dev/null 2>&1 &";
            cmd = String.format(cmd, id, id);
            String namespaceName = "ns" + id;
            namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Shadowsocks getSocks(String id) {
        String ip = pppoeService.getIP(id);
        return getSocks(id, ip);
    }

    @Override
    public Shadowsocks getSocks(String id, String ip) {
        Shadowsocks shadowsocks = null;
        if (ip != null) {
            String cmd = "netstat -ln -tpe |grep "+port+" |grep " + ip;
            String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
            Pattern compile = Pattern.compile(s);
            String namespace = "ns" + id;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line = null;
            try {
                line = bufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (line != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String pid = matcher.group(3);
                    shadowsocks= (Shadowsocks)SocksPrototypeManager.getProtoType("Shadowsocks");
                    shadowsocks.setPid(pid);
                    shadowsocks.setIp(ip);
                    shadowsocks.setId(id);
                }
            }
        }
        return shadowsocks;
    }

    @Deprecated
    public List<Shadowsocks> getAllListenedShadowsocks() {
        String cmd = "ip -all netns exec netstat -ln -tpe |grep "+port;
        String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
        String line = null;
        Pattern compile = Pattern.compile(s);
        ArrayList<Shadowsocks> shadowsocksList = new ArrayList<>();
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String ip = matcher.group(1);
                    String ownerId = matcher.group(2);
                    String pid = matcher.group(3);
                    Shadowsocks shadowsocks = (Shadowsocks) SocksPrototypeManager.getProtoType("Shadowsocks");
                    shadowsocks.setPid(pid);
                    shadowsocks.setIp(ip);
                    shadowsocks.setId(ownerId.substring(2));
                    shadowsocksList.add(shadowsocks);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return shadowsocksList;
    }
}
