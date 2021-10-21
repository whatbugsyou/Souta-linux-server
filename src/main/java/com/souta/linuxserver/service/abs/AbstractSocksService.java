package com.souta.linuxserver.service.abs;


import com.souta.linuxserver.entity.abs.Socks;
import com.souta.linuxserver.service.NamespaceService;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSocksService {

    protected NamespaceService namespaceService;
    protected String port;
    protected Logger log;
    protected String configFileDir;
    protected Class<? extends Socks> socksProtoTypeClass;

    public AbstractSocksService(NamespaceService namespaceServicee) {
        this.namespaceService = namespaceService;
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

    public boolean isStart(String id, String ip) {
        if (ip != null) {
            String cmd = "netstat -lnt |grep " + ip + ":" + port;
            String namespaceName = "ns" + id;
            InputStream inputStream = namespaceService.exeCmdInNamespace(namespaceName, cmd);
            return hasOutput(inputStream);
        }
        return false;
    }

}
