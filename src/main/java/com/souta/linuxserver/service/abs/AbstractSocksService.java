package com.souta.linuxserver.service.abs;


import com.souta.linuxserver.entity.abs.Socks;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.NamespaceService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.SocksService;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSocksService<T extends Socks> implements SocksService {

    protected NamespaceService namespaceService;
    protected PPPOEService pppoeService;
    protected CommandService commandService;
    protected Integer listenPort;

    public AbstractSocksService(NamespaceService namespaceService, PPPOEService pppoeService, CommandService commandService, Integer listenPort) {
        this.namespaceService = namespaceService;
        this.pppoeService = pppoeService;
        this.commandService = commandService;
        this.listenPort = listenPort;
    }


    @Override
    public final boolean stopSocks(String id) {
        String cmd = "netstat -lntp |grep " + listenPort;
        // tcp        0      0 121.230.252.206:10809   0.0.0.0:*               LISTEN      65481/python2
        String s = ".*LISTEN\\s+(\\d+)/.*";
        Pattern compile = Pattern.compile(s);
        String namespace = "ns" + id;
        Process process = commandService.exec(namespace, cmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    String pid = matcher.group(1);
                    String cmd2 = "kill -9 " + pid;
                    commandService.execAndWaitForAndCloseIOSteam(cmd2);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            Process process = commandService.exec(namespaceName, cmd);
            try (InputStream inputStream = process.getInputStream();
                 OutputStream outputStream = process.getOutputStream();
                 InputStream errorStream = process.getErrorStream()
            ) {
                return inputStream.read() != -1;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    @Override
    public Socks getSocks(String id, String ip) {
        boolean isStart = isStart(id, ip);
        Socks socks = null;
        if (isStart) {
            socks = getSocksInstance();
            if (socks != null) {
                socks.setId(id);
                socks.setIp(ip);
            }
        }
        return socks;
    }

    public abstract T getSocksInstance();
}
