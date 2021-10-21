package com.souta.linuxserver.controller;

import cn.hutool.core.text.StrBuilder;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.impl.NamespaceServiceImpl;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Data
public class Host {
    public static final String java_server_host = "http://91vpn.cc";
    private static final Logger log = LoggerFactory.getLogger(Host.class);
    private static final String hostFilePath = "/root/host.json";
    private static final String DNSFilePath = "/etc/resolv.conf";
    public static String port = "18080";
    private NamespaceService namespaceService = new NamespaceServiceImpl();

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

}
