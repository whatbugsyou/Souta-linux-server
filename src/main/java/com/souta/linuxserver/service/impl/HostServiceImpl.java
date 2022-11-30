package com.souta.linuxserver.service.impl;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.HostService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class HostServiceImpl implements HostService {

    private static final String hostRouteFilePath = "/root/hostRoute.sh";
    private static final String DNSFilePath = "/etc/resolv.conf";
    private static final String ipRouteTablePath = "/etc/iproute2/rt_tables";
    private static final String hostRouteTablePrio = "100";
    private static final String hostRouteTableName = "hostRouteTable";

    private final HostConfig hostConfig;
    private final NamespaceService namespaceService;
    private final RateLimitService rateLimitService;

    public HostServiceImpl(HostConfig hostConfig, NamespaceService namespaceService, RateLimitService rateLimitService) {
        this.hostConfig = hostConfig;
        this.namespaceService = namespaceService;
        this.rateLimitService = rateLimitService;
    }


    @PostConstruct
    public void init() {
        initDNS();
        initFirewall();
        refreshIPRoute();
        monitorHostIp();
    }

    private void refreshIPRoute() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(ipRouteTablePath));
            String line;
            boolean flag = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals(String.format("%s %s", hostRouteTablePrio, hostRouteTableName))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                String cmd = String.format("echo \"%s %s\" >> %s", hostRouteTablePrio, hostRouteTableName, ipRouteTablePath);
                namespaceService.exeCmdInDefaultNamespace(cmd);
            }
            File file = new File(hostRouteFilePath);
            String cmd = "ip route";
            InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
            while ((line = bufferedReader.readLine()) != null) {
                bufferedWriter.write(String.format("ip route add %s table %s", line, hostRouteTableName));
                bufferedWriter.newLine();
            }
            bufferedWriter.flush();
            namespaceService.exeCmdInDefaultNamespace("sh " + hostRouteFilePath);
            namespaceService.exeCmdInDefaultNamespace("ip rule del from all table " + hostRouteTableName);
            namespaceService.exeCmdInDefaultNamespace("ip rule add from all table " + hostRouteTableName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initFirewall() {
        String startFirewalldService = "service firewalld start \n";
        String openWebservicePort = String.format("firewall-cmd --zone=public --add-port=%s/tcp --permanent \n", hostConfig.getHost().getPort());
        String openRemoteDebugPort = String.format("firewall-cmd --zone=public --add-port=%s/tcp --permanent \n", "5005");
        String reloadFirewalld = "service firewalld reload \n";
        StrBuilder strBuilder = new StrBuilder();
        strBuilder.append(startFirewalldService).append(openWebservicePort).append(openRemoteDebugPort).append(reloadFirewalld);
        File file = new File("/root/fireWalld.sh");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
            bufferedWriter.write(strBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        namespaceService.exeCmdInDefaultNamespace("sh /root/fireWalld.sh");
    }

    private void initDNS() {
        File file = new File(DNSFilePath);
        try (FileWriter fileWriter = new FileWriter(file);) {
            fileWriter.write("nameserver 114.114.114.114\n");
            fileWriter.write("nameserver 8.8.8.8\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void monitorHostIp() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        Runnable beeper = () -> {
            if (hostConfig.getHost().getId() != null) {
                String cmd = "ifconfig | grep destination|awk '{print $2}'";
                InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
                String nowIp = null;
                if (inputStream != null) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        nowIp = reader.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (nowIp != null && nowIp.matches("[\\\\.\\d]+")) {
                    String oldIp = hostConfig.getHost().getIp();
                    Boolean isIpChanged = nowIp.equals(oldIp);
                    if (isIpChanged) {
                        refreshIPRoute();
                        log.info("send new HOST IP " + nowIp);
                        if (sendNewHostIp()) {
                            hostConfig.getHost().setIp(nowIp);
                        }
                    }
                } else {
                    log.error("HOST IP is not found");
                }
            }
        };
        scheduler.scheduleAtFixedRate(beeper, 0, 10, TimeUnit.SECONDS);
    }

    private boolean sendNewHostIp() {
        try {
            String jsonStr = JSONObject.toJSONString(hostConfig.getHost());
            int status = HttpRequest
                    .put(hostConfig.getJavaServerHost() + "/v1.0/server")
                    .body(jsonStr, "application/json;charset=UTF-8")
                    .execute().getStatus();
            if (status != 200) {
                throw new ResponseNotOkException("error in refreshing  HostInfo to java server,API(PUT) :  /v1.0/server");
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public void updateRateLimit(Integer limitKB) {
        hostConfig.getHost().setRateLimitKB(limitKB);
        saveConfigFile();
        rateLimitService.removeAll();
    }

    private void saveConfigFile() {
        try (FileWriter fileWriter = new FileWriter(hostConfig.getFilePath());) {
            fileWriter.write(JSONObject.toJSONString(hostConfig.getHost()));
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
