package com.souta.linuxserver.proxy;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONReader;
import com.alibaba.fastjson.JSONWriter;
import com.souta.linuxserver.service.NamespaceCommandService;
import com.souta.linuxserver.v2raySupport.InBoundObject;
import com.souta.linuxserver.v2raySupport.OutBoundObject;
import com.souta.linuxserver.v2raySupport.RoutingObject;
import com.souta.linuxserver.v2raySupport.RuleObject;
import com.souta.linuxserver.v2raySupport.factory.FreedomOutBoundFactory;
import com.souta.linuxserver.v2raySupport.factory.ShadowsocksInBoundFactory;
import com.souta.linuxserver.v2raySupport.factory.Socks5InBoundFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;

@Service
public class ProxyServiceImpl implements ProxyService {

    private final static FreedomOutBoundFactory freedomOutBoundFactory = new FreedomOutBoundFactory();
    private final static ShadowsocksInBoundFactory shadowsocksInBoundFactory = new ShadowsocksInBoundFactory();
    private final static Socks5InBoundFactory socks5InBoundFactory = new Socks5InBoundFactory();
    private static final int MAX_PROXY_ID = 200;
    private final NamespaceCommandService commandService;
    private final ProxyConfig proxyConfig;

    public ProxyServiceImpl(NamespaceCommandService commandService, ProxyConfig proxyConfig) {
        this.commandService = commandService;
        this.proxyConfig = proxyConfig;
    }

    private synchronized void startMainProxy(String namespaceName) {
        if (!isProxyStart()) {
            createMainConfigFile();
            String cmd = "v2ray run -c /root/v2rayConfig/v2ray.json -c /root/v2rayConfig/routing.json >/dev/null 2>&1 &";
            commandService.execAndWaitForAndCloseIOSteam(cmd, namespaceName);
        }
    }

    private void createMainConfigFile() {
        File file = new File("/root/v2rayConfig/v2ray.json");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
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

        File file2 = new File("/root/v2rayConfig/routing.json");
        try (FileWriter fileWriter = new FileWriter(file2);
             JSONWriter jsonWriter = new JSONWriter(fileWriter);
        ) {
            RoutingObject routingObject = new RoutingObject();
            ArrayList<RuleObject> ruleObjects = new ArrayList<>();
            for (int i = 0; i < MAX_PROXY_ID; i++) {
                String lineId = String.valueOf(i);
                RuleObject ruleObject = new RuleObject();
                ruleObject.setInboundTag(new String[]{proxyConfig.getShadowsocksTag(lineId), proxyConfig.getSocks5Tag(lineId)});
                ruleObject.setOutboundTag(proxyConfig.getOutBoundTag(lineId));
                ruleObjects.add(ruleObject);
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
    public void startProxy(String proxyId, String listenIp, String namespaceName) {
        if (!isProxyStart()) {
            startMainProxy(namespaceName);
        }
        createConfigFile(proxyId, listenIp);
        String inboundConfigFilePath = proxyConfig.getInboundConfigFilePath(proxyId);
        String outboundConfigFilePath = proxyConfig.getOutboundConfigFilePath(proxyId);
        String cmd1 = "v2ray api adi " + inboundConfigFilePath;
        String cmd2 = "v2ray api ado " + outboundConfigFilePath;
        commandService.execAndWaitForAndCloseIOSteam(cmd1, namespaceName);
        commandService.execAndWaitForAndCloseIOSteam(cmd2, namespaceName);
    }

    private void createConfigFile(String proxyId, String listenIp) {
        String inboundConfigFilePath = proxyConfig.getInboundConfigFilePath(proxyId);
        String outboundConfigFilePath = proxyConfig.getOutboundConfigFilePath(proxyId);

        Integer ssPort = proxyConfig.getShadowsocksConfig(proxyId).getPort();
        String password =  proxyConfig.getShadowsocksConfig(proxyId).getPassword();
        String method =  proxyConfig.getShadowsocksConfig(proxyId).getMethod();

        String socksPassword = proxyConfig.getSocks5Config(proxyId).getPassword();
        String socksUsername = proxyConfig.getSocks5Config(proxyId).getUsername();
        int socksPort = proxyConfig.getSocks5Config().getPort();

        OutBoundObject freedomOutBound = freedomOutBoundFactory.getInstance(listenIp, "UseIP", proxyConfig.getOutBoundTag(proxyId));
        InBoundObject shadowsocksInBound = shadowsocksInBoundFactory.getInstance(listenIp, ssPort, method, password, proxyConfig.getShadowsocksTag(proxyId));
        InBoundObject socks5InBound = socks5InBoundFactory.getInstance(listenIp, socksPort, socksUsername, socksPassword, proxyConfig.getSocks5Tag(proxyId));

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
        inBound.put("inBounds", new Object[]{shadowsocksInBound, socks5InBound});
        try (FileWriter fileWriter = new FileWriter(inboundConfigFilePath);
             JSONWriter jsonWriter = new JSONWriter(fileWriter)) {
            jsonWriter.writeObject(inBound);
            jsonWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean isProxyStart(String listenIp, String namespaceName) {
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
