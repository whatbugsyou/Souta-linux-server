package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.Socks5Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Socks5ServiceImpl implements Socks5Service {
    private static final Logger log = LoggerFactory.getLogger(Socks5ServiceImpl.class);

    static {
        try {
            File file = new File("/var/run/ss5");
            if (!file.exists()) {
                file.mkdir();
            }
            File file1 = new File("/tmp/ss5.passwd");
            if (!file1.exists()) {
                FileWriter fileWriter = new FileWriter(file1);
                fileWriter.write("test123 test123");
                fileWriter.flush();
            }
            File file2 = new File("/tmp/ss5.conf");
            if (!file2.exists()) {
                FileWriter fileWriter = new FileWriter(file1);
                fileWriter.write("auth 0.0.0.0/0 - u\n");
                fileWriter.write("permit u 0.0.0.0/0 - 0.0.0.0/0 - - - - -\n");

            }
            File file3 = new File("/tmp/ss5.log");
            if (!file3.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final NamespaceService namespaceService;
    private final PPPOEService pppoeService;

    public Socks5ServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService) {
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
    }

    static boolean hasOutput(InputStream inputStream) {
        int read = 0;
        try {
            read = inputStream.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return read != -1;
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = "ls /tmp/socks5/ |grep socks5-" + id + ".sh";
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        return hasOutput(inputStream);
    }

    @Override
    public boolean isStart(String id, String ip) {
        if (ip != null) {
            String cmd = "netstat -ln -tpe |grep 10808 |grep " + ip;
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
    public boolean createConfigFile(String id) {
        String ip = pppoeService.getIP(id);
        return createConfigFile(id, ip);
    }

    @Override
    public boolean createConfigFile(String id, String ip) {
        if (ip == null) {
            return false;
        }
        File dir = new File("/tmp/socks5");
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, "socks5-" + id + ".sh");
        BufferedWriter cfgfileBufferedWriter = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            cfgfileBufferedWriter.write("export SS5_SOCKS_ADDR=" + ip + "\n");
            cfgfileBufferedWriter.write("export SS5_SOCKS_PORT=10808\n");
            cfgfileBufferedWriter.write("export SS5_CONFIG_FILE=/tmp/ss5.conf\n");
            cfgfileBufferedWriter.write("export SS5_PASSWORD_FILE=/tmp/ss5.passwd\n");
            cfgfileBufferedWriter.write("export SS5_LOG_FILE=/tmp/ss5.log\n");
            cfgfileBufferedWriter.write("export SS5_PROFILE_PATH=/tmp\n");
            String startCmd = "/usr/sbin/ss5 -t -m -u root";
            cfgfileBufferedWriter.write(startCmd);
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
    public Socks5 getSocks(String id) {
        String ip = pppoeService.getIP(id);
        return getSocks(id, ip);
    }

    @Override
    public Socks5 getSocks(String id, String ip) {
        Socks5 socks5 = null;
        if (ip != null) {
            String namespaceName = "ns" + id;
            String cmd = "netstat -ln -tpe |grep 10808 |grep " + ip;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
            String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
            Pattern compile = Pattern.compile(s);
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
                    socks5 = (Socks5) SocksPrototypeManager.getProtoType("Socks5");
                    socks5.setPid(pid);
                    socks5.setId(id);
                    socks5.setIp(ip);
                }
            }
        }
        return socks5;
    }

    @Override
    public boolean stopSocks(String id) {
        String cmd = "netstat -ln -tpe |grep 10808";
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
    public boolean restartSocks(String id) {
        if (stopSocks(id)) {
            return startSocks(id);
        } else {
            return false;
        }
    }

    @Override
    public boolean startSocks(String id) {
        String ip = pppoeService.getIP(id);
        return startSocks(id, ip);

    }

    @Override
    public boolean startSocks(String id, String ip) {
        if (createConfigFile(id, ip)) {
            String namespaceName = "ns" + id;
            String cmd = "sh /tmp/socks5/socks5-" + id + ".sh";
            namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<Socks5> getAllListenedSocks() {
        String cmd = "ip -all netns exec netstat -ln -tpe |grep 10808";
        String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
        String line;
        Pattern compile = Pattern.compile(s);
        ArrayList<Socks5> socks5List = new ArrayList<>();
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String ip = matcher.group(1);
                    String ownerId = matcher.group(2);
                    String pid = matcher.group(3);
                    String port = "10808";
                    Socks5 socks5 = new Socks5();
                    socks5.setIp(ip);
                    socks5.setUsername("test123");
                    socks5.setPassword("test123");
                    socks5.setPid(pid);
                    socks5.setPort(port);
                    socks5.setId(ownerId.substring(2));
                    socks5List.add(socks5);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return socks5List;
    }
}
