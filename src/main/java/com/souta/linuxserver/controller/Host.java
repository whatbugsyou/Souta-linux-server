package com.souta.linuxserver.controller;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.impl.NamespaceServiceImpl;
import com.souta.linuxserver.util.FileUtil;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Data
public class Host {
    public static final String java_server_host = "https://i.souta.com";
    private static final Logger log = LoggerFactory.getLogger(Host.class);
    private static final String hostFilePath = "/root/host.json";
    private static final String hostRouteFilePath = "/root/hostRoute.sh";
    private static final String DNSFilePath = "/etc/resolv.conf";
    private static final String ipRouteTablePath = "/etc/iproute2/rt_tables";
    private static final String hostRouteTablePrio = "100";
    private static final String hostRouteTableName = "hostRouteTable";
    public static String id;
    public static String port = "18080";
    public static String IP = null;
    public static Integer VERSION = null;
    private NamespaceService namespaceService = new NamespaceServiceImpl();

    private static void registerHost() throws Exception {
        String jsonStr = FileUtil.ReadFile(hostFilePath);
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        HttpResponse execute = HttpRequest
                .put(java_server_host + "/v1.0/server")
                .body(jsonStr, "application/json;charset=UTF-8")
                .execute();
        if (execute.getStatus() != 200) {
            throw new ResponseNotOkException("error in sending registerHost from java server,API(PUT) :  /v1.0/server");
        }
        String responseBody = execute.body();
        JSONObject response = JSON.parseObject(responseBody);
        Object id = response.get("id");
        if (id == null) {
            throw new NullPointerException("id is null");
        } else {
            Host.id = String.valueOf(id);
            VERSION = response.getInteger("version");
            jsonObject.put("id", Host.id);
        }
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(hostFilePath);
            fileWriter.write(jsonObject.toJSONString());
            fileWriter.flush();
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

    public void init() {
        initDNS();
        initFirewall();
        initIPRoute();
        try {
            initHostId();
        } catch (FileNotFoundException e) {
            log.error(e.getMessage());
            System.exit(1);
        }
        monitorHostIp();
    }

    private void initIPRoute() {
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
            fileWriter.write("nameserver 114.114.114.114\n");
            fileWriter.write("nameserver 8.8.8.8\n");
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

    private void monitorHostIp() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        Runnable beeper = () -> {
            if (id != null) {
                String cmd = "ifconfig | grep destination|awk '{print $2}'";
                InputStream inputStream = namespaceService.exeCmdInDefaultNamespace(cmd);
                String nowIp = null;
                if (inputStream != null) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));// 读取文件
                        nowIp = reader.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (nowIp != null && nowIp.matches("[\\\\.\\d]+")) {
                    if (IP == null || !IP.equals(nowIp)) {
                        if (IP != null) {
                            initIPRoute();
                        }
                        log.info("send new HOST IP " + nowIp);
                        try {
                            String jsonStr = FileUtil.ReadFile(hostFilePath);
                            int status = HttpRequest
                                    .put(java_server_host + "/v1.0/server")
                                    .body(jsonStr, "application/json;charset=UTF-8")
                                    .execute().getStatus();
                            if (status != 200) {
                                throw new ResponseNotOkException("error in refreshing  HostInfo to java server,API(PUT) :  /v1.0/server");
                            }
                            IP = nowIp;
                        }catch (Exception e){
                            log.error(e.getMessage());
                        }
                    }
                } else {
                    log.error("HOST IP is not found");
                }
            }
        };
        scheduler.scheduleAtFixedRate(beeper, 0, 10, TimeUnit.SECONDS);
    }

    private void initHostId() throws FileNotFoundException {
        File file = new File(hostFilePath);
        if (!file.exists()) {
            throw new FileNotFoundException(hostFilePath);
        }
        String jsonStr = FileUtil.ReadFile(hostFilePath);
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        port = (String) jsonObject.get("port");
        id = jsonObject.getString("id");
        if (id == null) {
            try {
                registerHost();
            } catch (Exception e) {
                log.error(e.getMessage());
                System.exit(1);
            }
        }
    }

}
