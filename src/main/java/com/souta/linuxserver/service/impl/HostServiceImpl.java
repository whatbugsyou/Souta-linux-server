package com.souta.linuxserver.service.impl;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.HostService;
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
    private final CommandService commandService;

    public HostServiceImpl(HostConfig hostConfig, CommandService commandService) {
        this.hostConfig = hostConfig;
        this.commandService = commandService;
    }

    @PostConstruct
    public void init() {
        initDNS();
        initFirewall();
        refreshIPRoute();
        initHostId();
        monitorHostIp();
    }

    private void initHostId() {
        if (hostConfig.getHost().getId() == null) {
            try {
                registerHost();
            } catch (Exception e) {
                log.error(e.getMessage());
                System.exit(1);
            }
        }
    }

    private void registerHost() throws Exception {
        String jsonStr = JSONObject.toJSONString(hostConfig.getHost());
        try (HttpResponse response = HttpRequest
                .put(hostConfig.getJavaServerHost() + "/v1.0/server")
                .body(jsonStr, "application/json;charset=UTF-8")
                .execute()) {

            if (response.getStatus() != 200) {
                throw new ResponseNotOkException("error in sending registerHost from java server,API(PUT) :  /v1.0/server");
            }
            String responseBody = response.body();
            JSONObject body = JSON.parseObject(responseBody);
            Object id = body.get("id");
            if (id == null) {
                throw new NullPointerException("id is null");
            } else {
                hostConfig.getHost().setId(String.valueOf(id));
            }
        }
        saveConfigFile();
    }

    private void refreshIPRoute() {
        String line;
        try (FileReader fileReader = new FileReader(ipRouteTablePath);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            boolean flag = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals(String.format("%s %s", hostRouteTablePrio, hostRouteTableName))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                String cmd = String.format("echo \"%s %s\" >> %s", hostRouteTablePrio, hostRouteTableName, ipRouteTablePath);
                commandService.execCmd(cmd, true, Namespace.DEFAULT_NAMESPACE.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        File file = new File(hostRouteFilePath);
        String cmd = "ip route";
        Process process = commandService.exeCmdInDefaultNamespace(cmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
             FileWriter fileWriter = new FileWriter(file);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)
        ) {
            while ((line = bufferedReader.readLine()) != null) {
                bufferedWriter.write(String.format("ip route add %s table %s", line, hostRouteTableName));
                bufferedWriter.newLine();
            }
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        commandService.exeCmdInDefaultNamespaceAndWaitForCloseIOStream("sh " + hostRouteFilePath);
        commandService.exeCmdInDefaultNamespaceAndWaitForCloseIOStream("ip rule del from all table " + hostRouteTableName);
        commandService.exeCmdInDefaultNamespaceAndWaitForCloseIOStream("ip rule add from all table " + hostRouteTableName);
    }

    private void initFirewall() {
        File file = new File("/root/fireWalld.conf");
        try (FileWriter fileWriter = new FileWriter(file);
             BufferedWriter cfgfileBufferedWriter = new BufferedWriter(fileWriter);
             InputStream configStream = this.getClass().getResourceAsStream("/static/mainFirewalld.conf");
             InputStreamReader inputStreamReader = new InputStreamReader(configStream);
             BufferedReader tmpbufferedReader = new BufferedReader(inputStreamReader)) {
            String line;
            while (((line = tmpbufferedReader.readLine()) != null)) {
                line = line.replace("{sshPort}", "22");
                line = line.replace("{listenPort}", hostConfig.getHost().getPort());
                line = line.replace("{remoteDebugPort}", "5005");
                cfgfileBufferedWriter.write(line);
                cfgfileBufferedWriter.newLine();
            }
            cfgfileBufferedWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String cmd = "iptables-restore " + "/root/fireWalld.conf";
        commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, Namespace.DEFAULT_NAMESPACE.getName());
    }

    private void initDNS() {
        File file = new File(DNSFilePath);
        try (FileWriter fileWriter = new FileWriter(file)) {
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
                Process process = commandService.exeCmdWithNewSh(cmd);
                try (InputStream inputStream = process.getInputStream();
                     OutputStream outputStream = process.getOutputStream();
                     InputStream errorStream = process.getErrorStream();
                     InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                     BufferedReader reader = new BufferedReader(inputStreamReader)
                ) {
                    String nowIp = null;
                    nowIp = reader.readLine();
                    if (nowIp != null && nowIp.matches("[\\\\.\\d]+")) {
                        String oldIp = hostConfig.getHost().getIp();
                        boolean isIpChanged = !nowIp.equals(oldIp);
                        if (isIpChanged) {
                            refreshIPRoute();
                            log.info("send new HOST IP " + nowIp);
                            if (sendNewHostIp()) {
                                hostConfig.getHost().setIp(nowIp);
                            }
                        }
                    } else {
                        log.error("HOST IP is not found, start dialing ppp0");
                        String dialcmd = "ifup ppp0";
                        commandService.exeCmdInDefaultNamespaceAndWaitForCloseIOStream(dialcmd);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        scheduler.scheduleAtFixedRate(beeper, 0, 10, TimeUnit.SECONDS);
    }

    private boolean sendNewHostIp() {
        String jsonStr = JSONObject.toJSONString(hostConfig.getHost());
        try (HttpResponse response = HttpRequest
                .put(hostConfig.getJavaServerHost() + "/v1.0/server")
                .body(jsonStr, "application/json;charset=UTF-8")
                .execute()) {
            int status = response.getStatus();
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
    }

    private void saveConfigFile() {
        try (FileWriter fileWriter = new FileWriter(hostConfig.getFilePath())) {
            fileWriter.write(JSONObject.toJSONString(hostConfig.getHost()));
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
