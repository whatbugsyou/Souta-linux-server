package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.ShadowsocksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ShadowsocksServiceImpl implements ShadowsocksService {
    private static final Logger log = LoggerFactory.getLogger(ShadowsocksService.class);
    private final NamespaceService namespaceService;
    private final PPPOEService pppoeService;

    public ShadowsocksServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService) {
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = String.format("ls /tmp/shadowsocks/ |grep shadowsocks-%s.json", id);
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        return hasOutput(inputStream);
    }

    @Override
    public boolean createShadowsocksConfigfile(String id, String ip) {
        if (ip == null) {
            return false;
        }
        File dir = new File("/tmp/shadowsocks");
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, String.format("shadowsocks-%s.json", id));
        BufferedWriter cfgfileBufferedWriter = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            cfgfileBufferedWriter.write("{\n");
            cfgfileBufferedWriter.write("\"server\":\"" + ip + "\",\n");
            cfgfileBufferedWriter.write("\"server_port\":10809,\n");
            cfgfileBufferedWriter.write("\"local_address\": \"127.0.0.1\",\n");
            cfgfileBufferedWriter.write("\"local_port\":1080,\n");
            cfgfileBufferedWriter.write("\"password\":\"test123\",\n");
            cfgfileBufferedWriter.write("\"timeout\":600,\n");
            cfgfileBufferedWriter.write("\"method\":\"aes-256-cfb\"\n");
            cfgfileBufferedWriter.write("}\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
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
    public boolean createShadowsocksConfigfile(String id) {
        String ip = pppoeService.getIP(id);
        return createShadowsocksConfigfile(id, ip);
    }

    @Override
    public boolean stopShadowsocks(String id) {
        String cmd = "netstat -ln -tpe |grep 10809";
        String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
        Pattern compile = Pattern.compile(s);
        String namespace = "ns" + id;
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String pid = matcher.group(3);
                    String cmd2 = "kill -9 " + pid;
                    namespaceService.exeCmdInDefaultNamespace(cmd2);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean restartShadowsocks(String id) {
        if (stopShadowsocks(id)) {
            return startShadowsocks(id);
        } else {
            return false;
        }
    }

    @Override
    public boolean startShadowsocks(String id, String ip) {
        if (createShadowsocksConfigfile(id, ip)) {
            String cmd = "ssserver -c /tmp/shadowsocks/shadowsocks-%s.json";
            cmd = String.format(cmd, id, id);
            String namespaceName = "ns" + id;
            namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean startShadowsocks(String id) {
        String ip = pppoeService.getIP(id);
        return startShadowsocks(id, ip);
    }

    @Override
    public boolean isStart(String id, String ip) {
        if (ip != null) {
            String cmd = "netstat -ln -tpe |grep 10809 |grep " + ip;
            String namespaceName = "ns" + id;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return hasOutput(inputStream);
        }
        return false;
    }

    @Override
    public boolean isStart(String id) {
        String ip = pppoeService.getIP(id);
        return isStart(id, ip);
    }

    @Override
    public Shadowsocks getShadowsocks(String id, String ip) {
        Shadowsocks shadowsocks = null;
        if (ip != null) {
            String cmd = "netstat -ln -tpe |grep 10809 |grep " + ip;
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
                    shadowsocks = (Shadowsocks) SocksPrototypeManager.getProtoType("Shadowsocks");
                    shadowsocks.setPid(pid);
                    shadowsocks.setIp(ip);
                    shadowsocks.setId(id);
                }
            }
        }
        return shadowsocks;
    }

    @Override
    public Shadowsocks getShadowsocks(String id) {
        String ip = pppoeService.getIP(id);
        return getShadowsocks(id, ip);
    }

    @Override
    @Deprecated
    public List<Shadowsocks> getAllListenedShadowsocks() {
        String cmd = "ip -all netns exec netstat -ln -tpe |grep 10809";
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

    private boolean hasOutput(InputStream inputStream) {
        return Socks5ServiceImpl.hasOutput(inputStream);
    }
}
