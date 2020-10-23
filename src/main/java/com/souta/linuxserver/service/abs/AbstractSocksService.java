package com.souta.linuxserver.service.abs;


import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.SocksService;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSocksService implements SocksService {
    protected NamespaceService namespaceService;
    protected PPPOEService pppoeService;
    protected String port;
    protected Logger log;
    protected String configFileDir;

    public AbstractSocksService(NamespaceService namespaceService, PPPOEService pppoeService) {
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
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
    public final boolean isStart(String id) {
        String ip = pppoeService.getIP(id);
        return isStart(id, ip);
    }

    @Override
    public final boolean stopSocks(String id) {
        String cmd = "netstat -ln -tpe |grep " + port;
        String s = ".*? ([\\\\.\\d]+?):.*LISTEN\\s+(\\d+)\\s+\\d+\\s+(\\d+)/.*";
        Pattern compile = Pattern.compile(s);
        String namespace = "ns" + id;
        InputStream inputStream = namespaceService.exeCmdInNamespace(namespace, cmd);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String pid = matcher.group(3);
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
    public final boolean restartSocks(String id) {
        if (stopSocks(id)) {
            return startSocks(id);
        } else {
            return false;
        }
    }

    @Override
    public final boolean startSocks(String id) {
        String ip = pppoeService.getIP(id);
        return startSocks(id, ip);
    }

    @Override
    public final boolean createConfigFile(String id) {
        String ip = pppoeService.getIP(id);
        return createConfigFile(id, ip);
    }

    @Override
    public final boolean isStart(String id, String ip) {
        if (ip != null) {
            String cmd = "netstat -ln -tpe |grep " + port + " |grep " + ip;
            String namespaceName = "ns" + id;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return hasOutput(inputStream);
        }
        return false;
    }
}
