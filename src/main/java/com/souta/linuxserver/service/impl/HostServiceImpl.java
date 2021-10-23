package com.souta.linuxserver.service.impl;

import cn.hutool.core.text.StrBuilder;
import com.souta.linuxserver.service.HostService;
import com.souta.linuxserver.service.NamespaceService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class HostServiceImpl implements HostService {
    private static final String DNSFilePath = "/etc/resolv.conf";
    public static String port = "18080";
    private final NamespaceService namespaceService;

    public HostServiceImpl(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @PostConstruct()
    public void init() {
        initDNS();
        initFirewall();
    }

    private void initFirewall() {
        String startFirewalldService = "service firewalld start \n";
        String openWebservicePort = String.format("firewall-cmd --zone=public --add-port=%s/tcp --permanent \n", port);
        String openRemoteDebugPort = String.format("firewall-cmd --zone=public --add-port=%s/tcp --permanent \n", "5005");
        String reloadFirewalld = "service firewalld reload \n";
        StrBuilder strBuilder = new StrBuilder();
        strBuilder.append(startFirewalldService).append(openWebservicePort).append(openRemoteDebugPort).append(reloadFirewalld);
        File file = new File("/root/fireWalld.sh");
        BufferedWriter bufferedWriter = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            bufferedWriter = new BufferedWriter(new FileWriter(file));
            bufferedWriter.write(strBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        namespaceService.exeCmdInDefaultNamespace("sh /root/fireWalld.sh");
    }

    private void initDNS() {
        File file = new File(DNSFilePath);
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            fileWriter.write("nameserver 8.8.8.8\n");
            fileWriter.write("nameserver 114.114.114.114\n");
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

    }

    @Override
    public List<String> getAllIp() {
        ArrayList<String> ipList = new ArrayList<>();
        String cmd = " ip a|grep 'inet .*'|awk '{print $2}'|awk -F/ '{print $1}'";
        InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    if (!(line.startsWith("10.") || line.startsWith("100.") || line.startsWith("172.") || line.startsWith("192.") || line.startsWith("127."))) {
                        ipList.add(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ipList;
    }
}
