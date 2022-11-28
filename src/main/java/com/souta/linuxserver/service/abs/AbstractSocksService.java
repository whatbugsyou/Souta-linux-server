package com.souta.linuxserver.service.abs;


import com.souta.linuxserver.entity.abs.Socks;
import com.souta.linuxserver.entity.prototype.SocksPrototypeManager;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.SocksService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSocksService<T extends Socks> implements SocksService {

    private Class<T> tClass;

    protected NamespaceService namespaceService;
    protected PPPOEService pppoeService;
    protected Integer listenPort;
    protected Class<? extends Socks> socksProtoTypeClass;

    public AbstractSocksService(NamespaceService namespaceService, PPPOEService pppoeService, Integer listenPort) {
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
        this.listenPort = listenPort;
        this.tClass = (Class<T>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public final static boolean hasOutput(InputStream inputStream) {
        int read = 0;
        try {
            read = inputStream.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return read != -1;
    }

    @Override
    public final boolean stopSocks(String id) {
        String cmd = "netstat -lntp |grep " + listenPort;
        // tcp        0      0 121.230.252.206:10809   0.0.0.0:*               LISTEN      65481/python2
        String s = ".*LISTEN\\s+(\\d+)/.*";
        Pattern compile = Pattern.compile(s);
        String namespace = "ns" + id;
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
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


    @Override
    public final boolean restartSocks(String id, String listenIp) {
        if (stopSocks(id)) {
            return startSocks(id, listenIp);
        } else {
            return false;
        }
    }

    @Override
    public final boolean isStart(String id, String ip) {
        if (ip != null) {
            String cmd = "netstat -lnt |grep " + ip + ":" + listenPort;
            String namespaceName = "ns" + id;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return hasOutput(inputStream);
        }
        return false;
    }

    @Override
    public Socks getSocks(String id, String ip) {
        boolean isStart = isStart(id, ip);
        Socks socks = null;
        if (isStart) {
            socks = SocksPrototypeManager.getProtoType(tClass);
            socks.setId(id);
            socks.setIp(ip);
        }
        return socks;
    }
}
