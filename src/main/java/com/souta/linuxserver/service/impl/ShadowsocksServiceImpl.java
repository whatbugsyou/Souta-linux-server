package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.PPPOE;
import com.souta.linuxserver.entity.Shadowsocks;
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
    private final NamespaceService namespaceService;
    private final PPPOEService pppoeService;
    private static final Logger log = LoggerFactory.getLogger(ShadowsocksService.class);

    public ShadowsocksServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService) {
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = String.format("ls /tmp/shadowsocks/ |grep shadowsocks-%s.json",id);
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        return hasOutput(inputStream);
    }

    @Override
    public boolean createShadowsocksConfigfile(String id) {
        return createConfigFile(id);
    }

    private boolean createConfigFile(String id) {
        String ip = pppoeService.getIP(id);
        if (ip ==null) {
            return false;
        } else {
            PPPOE pppoe = pppoeService.getPPPOE(id);
            ip = pppoe.getOutIP();
        }

        File dir = new File("/tmp/shadowsocks");
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, String.format("shadowsocks-%s.json",id));
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
    public boolean stopShadowsocks(String id) {
        String cmd = "netstat -ln -tpe |grep 10809";
        String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
        Pattern compile = Pattern.compile(s);
        String namespace = "ns" + id;
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line ;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                if (line != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        String pid = matcher.group(3);
                        String cmd2 = "kill -9 " + pid;
                        namespaceService.exeCmdInDefaultNamespace(cmd);
                    }
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
    public synchronized boolean startShadowsocks(String id) {
        boolean exist = checkConfigFileExist(id);
        if (exist) {
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
    public boolean isStart(String id) {
        String ip = pppoeService.getIP(id);
        if (ip != null) {
            String cmd = "netstat -ln -tpe |grep 10809 |grep " + ip;
            String namespaceName = "ns" + id;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return hasOutput(inputStream);
        }
        return false;
    }

    @Override
    public Shadowsocks getShadowsocks(String id) {
        Shadowsocks shadowsocks = null;
        boolean exist = checkConfigFileExist(id);
        if (exist) {
            shadowsocks = new Shadowsocks();
            String ip = pppoeService.getIP(id);
            if (ip!=null) {
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
                        shadowsocks.setPid(pid);
                    }
                }
            }
            shadowsocks.setIp(ip);
            shadowsocks.setId(id);
            shadowsocks.setPassword("test123");
            shadowsocks.setPort("10809");
            shadowsocks.setEncryption("aes-256-cfb");
        }
        return shadowsocks;
    }

    @Override
    public List<Shadowsocks> getAllListenedShadowsocks() {
        String cmd = "ip -all netns exec netstat -ln -tpe |grep 10809";
        String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
        String line = null;
        Pattern compile = Pattern.compile(s);
        ArrayList<Shadowsocks> shadowsocks5List = new ArrayList<>();
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String ip = matcher.group(1);
                    String ownerId = matcher.group(2);
                    String pid = matcher.group(3);
                    String port = "10809";
                    Shadowsocks shadowsocks = new Shadowsocks();
                    shadowsocks.setIp(ip);
                    shadowsocks.setPassword("test123");
                    shadowsocks.setPid(pid);
                    shadowsocks.setPort(port);
                    shadowsocks.setId(ownerId.substring(2));
                    shadowsocks5List.add(shadowsocks);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return shadowsocks5List;
    }

    private boolean hasOutput(InputStream inputStream) {
        return Socks5ServiceImpl.hasOutput(inputStream);
    }
}
