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
    private static String location;
    private static String operator;
    private static String expirationTime;
    public static String IP = null;
    private static final Logger log = LoggerFactory.getLogger(Host.class);
    public static final String java_server_host = "http://106.55.13.147:8088";
    private static final String hostFilePath = "/tmp/host.json";
    private static final String DNSFilePath = "/etc/resolv.conf";
    private static final String ipRouteTablePath = "/etc/iproute2/rt_tables";
    private static final String hostRouteTablePrio = "100";
    private static final String hostRouteTableName = "hostRouteTable";
    private static final String hostRoute = "default via 192.168.44.2 dev ens37 proto dhcp metric 101";
    private NamespaceService namespaceService = new NamespaceServiceImpl();


    public void init() {
//        initIPRoute();
        initDNS();
        initFirewall();
        initHostId();
        monitorHostIp();
    }

    private void initIPRoute() {
        //TODO maching different origin network enviroment
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(ipRouteTablePath));
            String line ;
            boolean flag = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals(hostRouteTableName)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                String cmd = String.format("echo \"%s %s\" >> %s", hostRouteTablePrio, hostRouteTableName, ipRouteTablePath);
                namespaceService.exeCmdInNamespace("", cmd);
            }
            String cmd = String.format("ip route add %s table %s", hostRoute, hostRouteTableName);
            namespaceService.exeCmdInNamespace("", cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initFirewall() {
        String cmd = String.format("iptables -I INPUT -p tcp --dport %s -j ACCEPT",port);
        namespaceService.exeCmdInNamespace("", cmd);
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
                InputStream inputStream = namespaceService.exeCmdInNamespace("", cmd);
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
        scheduler.scheduleAtFixedRate(beeper, 10, 10, TimeUnit.SECONDS);
    }

    private void initHostId() {
        File file = new File(hostFilePath);
        if (!file.exists()) {
            log.info("file not found {}", hostFilePath);
            System.exit(1);
        }
        String jsonStr = FileUtil.ReadFile(hostFilePath);
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        port = (String) jsonObject.get("port");
        location = (String) jsonObject.get("location");
        operator = (String) jsonObject.get("operator");
        expirationTime = (String) jsonObject.get("expirationTime");
        String id = jsonObject.getString("id");
        if ( (Host.id =id) == null) {
            if (!registerHost()){
                log.info("registerHost false");
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
