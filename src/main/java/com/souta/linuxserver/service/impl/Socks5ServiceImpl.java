package com.souta.linuxserver.service.impl;

import com.alibaba.fastjson.JSON;
import com.souta.linuxserver.dto.Socks5Info;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.Socks5Service;
import com.souta.linuxserver.service.abs.AbstractSocksService;
import com.souta.linuxserver.util.FileUtil;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
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

    private String authDir = "/root/socksAuth";

    public Socks5ServiceImpl(NamespaceService namespaceService) {
        super(namespaceService);
        this.namespaceService = namespaceService;
        this.port = DEFAULT_PORT;
        this.log = LoggerFactory.getLogger(Socks5ServiceImpl.class);
        this.configFileDir = "/root/socks5config";
        this.socksProtoTypeClass = Socks5.class;
    }

    @Override
    public boolean checkConfigFileExist(String ip) {
        String cmd = "ls " + configFileDir + " |grep socks5-" + ip + ".json";
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        return hasOutput(inputStream);
    }

    public boolean createStartScript(String ip) {
        if (ip == null) {
            return false;
        }
        File dir = new File(configFileDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
        if (!checkConfigFileExist(ip)){
            return false;
        }
        File configFile = new File(dir, "socks5-" + ip + ".json");
        String configJsonString = FileUtil.ReadFile(configFile.getPath());
        Socks5Info socks5Info = JSON.parseObject(configJsonString, Socks5Info.class);
        File scriptFile = new File(dir, "socks5-" + ip + ".sh");
        BufferedWriter cfgfileBufferedWriter = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(scriptFile);
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            cfgfileBufferedWriter.write("export SS5_SOCKS_ADDR=" + ip + "\n");
            cfgfileBufferedWriter.write("export SS5_SOCKS_PORT=" + socks5Info.getPort() + "\n");
            cfgfileBufferedWriter.write("export SS5_CONFIG_FILE=/root/ss5.conf\n");
            cfgfileBufferedWriter.write("export SS5_PASSWORD_FILE=" + authDir + "ss5-" + ip + ".passwd\n");
            cfgfileBufferedWriter.write("export SS5_LOG_FILE=/root/ss5.log\n");
            cfgfileBufferedWriter.write("export SS5_PROFILE_PATH=/root\n");
            String startCmd = "/usr/sbin/ss5 -t -m -u socks" + ip + " -p /var/run/ss5/ss5-" + ip + ".pid";
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

    public boolean createConfigFile(Socks5Info socks5Info) {
        String ip = socks5Info.getIp();
        if (ip == null) {
            return false;
        }
        File dir = new File(configFileDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File file = new File(dir, "socks5-" + ip + ".json");
        BufferedWriter cfgfileBufferedWriter = null;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            cfgfileBufferedWriter = new BufferedWriter(fileWriter);
            cfgfileBufferedWriter.write(JSON.toJSONString(socks5Info));
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
        changeAuth(socks5Info.getIp(), socks5Info.getAuthList());
        return true;
    }

    private void changeAuth(String ip, List<Socks5Info.authInfo> authList) {
        File file1 = new File(authDir + "/ss5" + ip + ".passwd");
        try {
            if (!file1.exists()) {
                FileWriter fileWriter = null;
                fileWriter = new FileWriter(file1);
                for (Socks5Info.authInfo authInfo : authList) {
                    fileWriter.write(authInfo.getUsername() + " " + authInfo.getPassword());
                }
                fileWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean startSocks(String ip) {
        if (createStartScript(ip)) {
            File configFile = new File(configFileDir, "socks5-" + ip + ".json");
            String configJsonString = FileUtil.ReadFile(configFile.getPath());
            Socks5Info socks5InfoOrigin = JSON.parseObject(configJsonString, Socks5Info.class);
            if (!isStart(ip, socks5InfoOrigin.getPort().toString())) {
                String cmd = "sh " + configFileDir + "socks5-" + ip + ".sh";
                namespaceService.exeCmdInDefaultNamespace(cmd);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final boolean isStart(String ip, String port) {
        if (ip != null) {
            String cmd = "netstat -lnt |grep " + ip + ":" + port;
            InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
            return hasOutput(inputStream);
        }
        return false;
    }

    @Override
    public void updateConfig(Socks5Info socks5Info) {
        File dir = new File(configFileDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File configFile = new File(configFileDir, "socks5-" + socks5Info.getIp() + ".json");
        String configJsonString = FileUtil.ReadFile(configFile.getPath());
        Socks5Info socks5InfoOrigin = JSON.parseObject(configJsonString, Socks5Info.class);
        if (socks5Info.getAuthList() == null) {
            socks5Info.setAuthList(socks5InfoOrigin.getAuthList());
        }
        if (socks5Info.getPort() == null) {
            socks5Info.setPort(socks5InfoOrigin.getPort());
        }
        if (socks5Info.getStatus() == null) {
            socks5Info.setStatus(socks5InfoOrigin.getStatus());
        }
        createConfigFile(socks5Info);
        if (socks5Info.getStatus() == true) {
            startSocks(socks5Info.getIp());
        } else {
            stopSocks(socks5Info.getIp());
        }
    }

    @Override
    public List<Socks5Info> getAllSocks5() {
        ArrayList<Socks5Info> socks5Infos = new ArrayList<>();
        List<String> allIp = getALLIp();
        allIp.forEach(ip -> {
            Socks5Info socks5Info = getSocks5(ip);
            socks5Infos.add(socks5Info);
        });
        return socks5Infos;
    }

    private List<String> getALLIp() {
        ArrayList<String> ipList = new ArrayList<>();
        String cmd = " ip a|grep 'inet .*'|awk '{print $2}'|awk -F/ '{print $1}'";
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    if (!(line.startsWith("10.") || line.startsWith("100.") || line.startsWith("172.") || line.startsWith("192."))) {
                        ipList.add(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public Socks5Info getSocks5(String ip) {
        File configFile = new File(configFileDir, "socks5-" + ip + ".json");
        String configJsonString = FileUtil.ReadFile(configFile.getPath());
        Socks5Info socks5Info = JSON.parseObject(configJsonString, Socks5Info.class);
        if (socks5Info != null) {
            socks5Info.setStatus(isStart(socks5Info.getIp(), socks5Info.getPort().toString()));
        } else {
            socks5Info = new Socks5Info(ip);
            createConfigFile(socks5Info);
        }
        return socks5Info;
    }

    @Override
    public boolean stopSocks(String ip) {
        String cmd = "netstat -lntp | grep ss5 | grep " + ip+":" ;
        String s = ".*LISTEN\\s+(\\d+)/.*";
        Pattern compile = Pattern.compile(s);
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String pid = matcher.group(1);
                    String cmd2 = "kill -9 " + pid;
                    namespaceService.exeCmdInDefaultNamespace(cmd2);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}
