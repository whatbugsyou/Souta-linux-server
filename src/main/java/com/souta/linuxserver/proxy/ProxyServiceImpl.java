package com.souta.linuxserver.proxy;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONReader;
import com.alibaba.fastjson.JSONWriter;
import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.line.LineBuildConfig;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.v2raySupport.InBoundObject;
import com.souta.linuxserver.v2raySupport.OutBoundObject;
import com.souta.linuxserver.v2raySupport.RoutingObject;
import com.souta.linuxserver.v2raySupport.RuleObject;
import com.souta.linuxserver.v2raySupport.factory.FreedomOutBoundFactory;
import com.souta.linuxserver.v2raySupport.factory.ShadowsocksInBoundFactory;
import com.souta.linuxserver.v2raySupport.factory.Socks5InBoundFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

@Service
public class ProxyServiceImpl implements ProxyService {

    private final static FreedomOutBoundFactory freedomOutBoundFactory = new FreedomOutBoundFactory();
    private final static ShadowsocksInBoundFactory shadowsocksInBoundFactory = new ShadowsocksInBoundFactory();
    private final static Socks5InBoundFactory socks5InBoundFactory = new Socks5InBoundFactory();
    private final CommandService commandService;
    private final ProxyConfig proxyConfig;
    private final LineBuildConfig lineBuildConfig;


    public ProxyServiceImpl(CommandService commandService, ProxyConfig proxyConfig, LineBuildConfig lineBuildConfig) {
        this.commandService = commandService;
        this.proxyConfig = proxyConfig;
        this.lineBuildConfig = lineBuildConfig;
    }


    @PostConstruct
    public void initProxy() {
        if (!isProxyStart()) {
            startProxy();
        }
    }

    private void startProxy() {
        createConfigFile();
        String namespaceName = lineBuildConfig.getServerNamespaceName();
        String cmd = "v2ray run -c /root/v2ray/v2ray.json -c /root/v2ray/routing.json >/dev/null 2>&1 &";
        commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
    }

    private void createConfigFile() {
        File file = new File("/root/v2ray/v2ray.json");
        try (FileWriter fileWriter = new FileWriter(file);
             JSONWriter jsonWriter = new JSONWriter(fileWriter);
             InputStream v2rayConfigStream = this.getClass().getResourceAsStream("/static/v2rayConfig.json");
             InputStreamReader inputStreamReader = new InputStreamReader(v2rayConfigStream);
             JSONReader jsonReader = new JSONReader(inputStreamReader);
        ) {
            Object o = jsonReader.readObject();
            jsonWriter.writeObject(o);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File file2 = new File("/root/v2ray/routing.json");
        try (FileWriter fileWriter = new FileWriter(file2);
             JSONWriter jsonWriter = new JSONWriter(fileWriter);
        ) {
            RoutingObject routingObject = new RoutingObject();
            ArrayList<RuleObject> ruleObjects = new ArrayList<>();
            Iterator<ADSL> iterator = lineBuildConfig.getADSLIterator();
            int i = 1;
            while (iterator.hasNext()) {
                ADSL adsl = iterator.next();
                String lineId = String.valueOf(i);
                RuleObject ruleObject = new RuleObject();
                ruleObject.setInboundTag(new String[]{lineBuildConfig.getShadowsocksTag(lineId), lineBuildConfig.getSocks5Tag(lineId)});
                ruleObject.setOutboundTag(new String[]{lineBuildConfig.getOutBoundTag(lineId)});
                i++;
            }
            routingObject.setRules(ruleObjects);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("routing", routingObject);
            jsonWriter.writeObject(jsonObject);
            jsonWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isProxyStart() {
        String cmd = "pgrep v2ray";
        Process process = commandService.exec(cmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream()
        ) {
            return inputStream.read() != -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startProxy(String lineId) {
        String namespaceName = lineBuildConfig.getServerNamespaceName();
        String inboundConfigFilePath = lineBuildConfig.getInboundConfigFilePath(lineId);
        String outboundConfigFilePath = lineBuildConfig.getOutboundConfigFilePath(lineId);
        File file1 = new File(inboundConfigFilePath);
        File file2 = new File(outboundConfigFilePath);
        if (!file1.exists() || !file2.exists()) {
            createConfigFile(lineId);
        }
        String cmd1 = "v2ray api adi " + inboundConfigFilePath;
        String cmd2 = "v2ray api ado " + outboundConfigFilePath;
        commandService.execAndWaitForAndCloseIOSteam(cmd1, namespaceName);
        commandService.execAndWaitForAndCloseIOSteam(cmd2, namespaceName);
    }

    private void createConfigFile(String lineId) {
        String inboundConfigFilePath = lineBuildConfig.getInboundConfigFilePath(lineId);
        String outboundConfigFilePath = lineBuildConfig.getOutboundConfigFilePath(lineId);

        String listenIp = lineBuildConfig.getListenIp(lineId);

        Integer ssPort = lineBuildConfig.getProxyConfig().getShadowsocksConfig().getPort();
        String password = lineBuildConfig.getProxyConfig().getShadowsocksConfig().getPassword();
        String method = lineBuildConfig.getProxyConfig().getShadowsocksConfig().getMethod();

        String socksPassword = lineBuildConfig.getProxyConfig().getSocks5Config().getPassword();
        String socksUsername = lineBuildConfig.getProxyConfig().getSocks5Config().getUsername();
        int socksPort = lineBuildConfig.getProxyConfig().getSocks5Config().getPort();

        OutBoundObject freedomOutBound = freedomOutBoundFactory.getInstance(listenIp, "UseIP", lineBuildConfig.getOutBoundTag(lineId));
        InBoundObject shadowsocksInBound = shadowsocksInBoundFactory.getInstance(listenIp, ssPort, method, password, lineBuildConfig.getShadowsocksTag(lineId));
        InBoundObject socks5InBound = socks5InBoundFactory.getInstance(listenIp, socksPort, socksUsername, socksPassword, lineBuildConfig.getSocks5Tag(lineId));

        JSONObject outBound = new JSONObject();
        outBound.put("outBounds", new Object[]{freedomOutBound});
        try (FileWriter fileWriter = new FileWriter(outboundConfigFilePath);
             JSONWriter jsonWriter = new JSONWriter(fileWriter)) {
            jsonWriter.writeObject(outBound);
            jsonWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JSONObject inBound = new JSONObject();
        outBound.put("inBounds", new Object[]{shadowsocksInBound, socks5InBound});
        try (FileWriter fileWriter = new FileWriter(inboundConfigFilePath);
             JSONWriter jsonWriter = new JSONWriter(fileWriter)) {
            jsonWriter.writeObject(inBound);
            jsonWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public boolean isProxyStart(String lineId) {
        String listenIp = lineBuildConfig.getListenIp(lineId);
        String namespaceName = lineBuildConfig.getServerNamespaceName();
        int listenPort1 = proxyConfig.getShadowsocksConfig().getPort();
        int listenPort2 = proxyConfig.getShadowsocksConfig().getPort();
        return isListen(listenIp, namespaceName, listenPort1) && isListen(listenIp, namespaceName, listenPort2);
    }

    private boolean isListen(String listenIp, String namespaceName, int listenPort) {
        String cmd = "netstat -lnt |grep " + listenIp + ":" + listenPort;
        Process process = commandService.exec(cmd, namespaceName);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream()
        ) {
            return inputStream.read() != -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
