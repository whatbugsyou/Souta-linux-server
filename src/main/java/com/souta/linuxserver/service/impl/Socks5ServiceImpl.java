package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.config.LineConfig;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.abs.AbstractSocks5Service;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Socks5ServiceImpl extends AbstractSocks5Service {

    private static final String configFileDir = "/root/socks5";

    public Socks5ServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService, LineConfig lineConfig) {
        super(namespaceService, pppoeService, lineConfig.getSocks5Config().getPort(), lineConfig.getSocks5Config());
    }

    @PostConstruct
    public void init() {
        try {
            File file = new File("/var/run/ss5");
            if (!file.exists()) {
                file.mkdir();
            }
            File file1 = new File("/root/ss5.passwd");
            if (!file1.exists()) {
                FileWriter fileWriter = new FileWriter(file1);
                fileWriter.write(socks5Config.getUsername() + " " + socks5Config.getPassword());
                fileWriter.flush();
            }
            File file2 = new File("/root/ss5.conf");
            if (!file2.exists()) {
                FileWriter fileWriter = new FileWriter(file2);
                fileWriter.write("auth 0.0.0.0/0 - u\n");
                fileWriter.write("permit u 0.0.0.0/0 - 0.0.0.0/0 - - - - -\n");
                fileWriter.flush();
            }
            File file3 = new File("/root/ss5.log");
            if (!file3.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean checkConfigFileExist(String id) {
        String cmd = "ls " + configFileDir + " |grep socks5-" + id + ".sh";
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
        File file = new File(dir, "socks5-" + id + ".sh");
        BufferedWriter cfgfileBufferedWriter = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            cfgfileBufferedWriter.write("export SS5_SOCKS_ADDR=" + ip + "\n");
            cfgfileBufferedWriter.write("export SS5_SOCKS_PORT=" + listenPort + "\n");
            cfgfileBufferedWriter.write("export SS5_CONFIG_FILE=/root/ss5.conf\n");
            cfgfileBufferedWriter.write("export SS5_PASSWORD_FILE=/root/ss5.passwd\n");
            cfgfileBufferedWriter.write("export SS5_LOG_FILE=/root/ss5.log\n");
            cfgfileBufferedWriter.write("export SS5_PROFILE_PATH=/root\n");
            String startCmd = "/usr/sbin/ss5 -t -m -u root -p /var/run/ss5/ss5-" + id + ".pid";
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
    public boolean startSocks(String id, String ip) {
        if (createConfigFile(id, ip)) {
            String namespaceName = Namespace.DEFAULT_PREFIX + id;
            String cmd = "sh /root/socks5/socks5-" + id + ".sh";
            namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public HashSet<String> getStartedIdSet() {
        HashSet<String> result = new HashSet<>();
        String cmd = " pgrep -a ss5|awk '/ss5-[0-9]+\\.pid/ {print $8}'";
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
