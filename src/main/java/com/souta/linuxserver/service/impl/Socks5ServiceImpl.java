package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
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

import static com.souta.linuxserver.entity.Socks5.*;

@Service
public class Socks5ServiceImpl extends AbstractSocksService implements Socks5Service {

    static {
        try {
            File file = new File("/var/run/ss5");
            if (!file.exists()) {
                file.mkdir();
            }
            File file1 = new File("/root/ss5.passwd");
            if (!file1.exists()) {
                FileWriter fileWriter = new FileWriter(file1);
                fileWriter.write(DEFAULT_USERNAME + " " + DEFAULT_PASSWORD);
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

    public Socks5ServiceImpl(NamespaceService namespaceService, PPPOEService pppoeService) {
        super(namespaceService, pppoeService);
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
        this.port = DEFAULT_PORT;
        this.log = LoggerFactory.getLogger(Socks5ServiceImpl.class);
        this.configFileDir = "/root/socks5";
        this.socksProtoTypeClass = Socks5.class;
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
            cfgfileBufferedWriter.write("export SS5_SOCKS_PORT=" + port + "\n");
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
