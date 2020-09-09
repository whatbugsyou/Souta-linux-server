package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
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
    public static String id;
    public static String port = "18080";
    public static String IP = null;
    private static final Logger log = LoggerFactory.getLogger(Host.class);
    public static final String java_server_host = "http://106.55.13.147:8088";
    private static final String hostFilePath = "/tmp/host.json";
    private static final String hostRouteFilePath = "/tmp/hostRoute.sh";
    private static final String DNSFilePath = "/etc/resolv.conf";
    private static final String ipRouteTablePath = "/etc/iproute2/rt_tables";
    private static final String hostRouteTablePrio = "100";
    private static final String hostRouteTableName = "hostRouteTable";
    private NamespaceService namespaceService = new NamespaceServiceImpl();


    public void init() {
        initDNS();
        initFirewall();
        initIPRoute();
        initHostId();
        monitorHostIp();
    }

    private void initIPRoute() {
        File file = new File(hostRouteFilePath);
        if (!file.exists()) {
            log.error("file not found : {}", hostRouteFilePath);
            System.exit(1);
        }

        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(ipRouteTablePath));
            String line ;
            boolean flag = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals(String.format("%s %s",hostRouteTablePrio,hostRouteTableName))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                String cmd = String.format("echo \"%s %s\" >> %s", hostRouteTablePrio, hostRouteTableName, ipRouteTablePath);
                namespaceService.exeCmdInDefaultNamespace(cmd);
            }
            namespaceService.exeCmdInDefaultNamespace("sh " + hostRouteFilePath);
            namespaceService.exeCmdInDefaultNamespace("ip rule del from all table " + hostRouteTableName);
            namespaceService.exeCmdInDefaultNamespace("ip rule add from all table " + hostRouteTableName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void initFirewall() {
        String startFirewalldService = "service firewalld start";
        String openWebservicePort = String.format("firewall-cmd --zone=public --add-port=%s/tcp --permanent", port);
        String openRemoteDebugPort = String.format("firewall-cmd --zone=public --add-port=%s/tcp --permanent", "5005");
        String reloadFirewalld = "service firewalld reload";
        File file = new File("/tmp/fireWalld.sh");
        BufferedWriter bufferedWriter = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            bufferedWriter = new BufferedWriter(new FileWriter(file));
            bufferedWriter.write(startFirewalldService);
            bufferedWriter.newLine();
            bufferedWriter.write(openWebservicePort);
            bufferedWriter.newLine();
            bufferedWriter.write(openRemoteDebugPort);
            bufferedWriter.newLine();
            bufferedWriter.write(reloadFirewalld);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (bufferedWriter!=null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        namespaceService.exeCmdInDefaultNamespace("sh /tmp/fireWalld.sh");
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
                Executors.newScheduledThreadPool(1);
        Runnable beeper = () -> {
            if (id != null) {
                String cmd = "curl members.3322.org/dyndns/getip";
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
                        IP = nowIp;
                        log.info("send new HOST IP " + nowIp);
                        String jsonStr = FileUtil.ReadFile(hostFilePath);
                        HttpRequest
                                .put(java_server_host + "/v1.0/server")
                                .body(jsonStr, "application/json;charset=UTF-8")
                                .execute();
                    }
                }
            }
        };
        scheduler.scheduleAtFixedRate(beeper, 0, 10, TimeUnit.SECONDS);
    }

    private void initHostId() {
        File file = new File(hostFilePath);
        if (!file.exists()) {
            log.error("file not found {}", hostFilePath);
            System.exit(1);
        }
        String jsonStr = FileUtil.ReadFile(hostFilePath);
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        port = (String) jsonObject.get("port");
        String id = jsonObject.getString("id");
        if ( (Host.id =id) == null) {
            if (!registerHost()){
                log.error("registerHost false");
                System.exit(1);
            }
        }

    }

    private static boolean registerHost() {
        String jsonStr = FileUtil.ReadFile(hostFilePath);
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        String responseBody = HttpRequest
                .put(java_server_host + "/v1.0/server")
                .body(jsonStr, "application/json;charset=UTF-8")
                .execute()
                .body();

        JSONObject response = JSON.parseObject(responseBody);
        Object id = response.get("id");
        if (id == null) {
            return false;
        } else {
            Host.id = String.valueOf(id) ;
            jsonObject.put("id", Host.id);
        }
        try {
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(hostFilePath);
                fileWriter.write(jsonObject.toJSONString());
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
        } catch (JSONException e) {
            log.info(e.getMessage());
        }
        return true;
    }

}
