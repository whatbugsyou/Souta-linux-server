package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.CommandService;
import com.souta.linuxserver.service.NamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NamespaceServiceImpl implements NamespaceService {
    private static final Logger log = LoggerFactory.getLogger(NamespaceServiceImpl.class);

    private final CommandService commandService;

    public NamespaceServiceImpl(CommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public boolean checkExist(String name) {
        if (null == name) return true;
        String cmd = "ls /var/run/netns/ |grep " + name + "$";
        Process process = commandService.exeCmdWithNewSh(cmd);
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
    public boolean checkExist(Namespace namespace) {
        if (namespace == null) {
            return false;
        } else {
            return checkExist(namespace.getName());
        }
    }

    @Override
    public List<Namespace> getAllNameSpace() {
        ArrayList<Namespace> namespaces = new ArrayList<>();
        String cmd = "ip netns list";
        Process process = commandService.exeCmdInDefaultNamespace(cmd);
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            String line;
            String pattern = "(.*?) .*";
            Pattern compile = Pattern.compile(pattern);

            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = compile.matcher(line);
                if (matcher.matches()) {
                    Namespace namespace = new Namespace(matcher.group(1));
                    namespaces.add(namespace);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return namespaces;
    }

    @Override
    public Namespace getNameSpace(String name) {
        boolean exist = checkExist(name);
        if (exist) {
            return new Namespace(name);
        }
        return null;
    }

    @Override
    public Namespace createNameSpace(String name) {
        boolean exist = checkExist(name);
        if (!exist) {
            String cmd = "ip netns add " + name;
            commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, Namespace.DEFAULT_NAMESPACE.getName());
        }
        return new Namespace(name);
    }

    @Override
    public boolean deleteNameSpace(String name) {
        boolean exist = checkExist(name);
        if (exist) {
            String cmd = "ip netns delete " + name;
            commandService.execCmdAndWaitForAndCloseIOSteam(cmd, false, Namespace.DEFAULT_NAMESPACE.getName());
        }
        return true;
    }


}
