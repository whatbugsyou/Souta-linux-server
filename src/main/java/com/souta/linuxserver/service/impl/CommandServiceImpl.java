package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.CommandService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Service
public class CommandServiceImpl implements CommandService {

    private final Runtime runtime = Runtime.getRuntime();

    @Override
    public Process exeCmdInNamespace(String namespace, String cmd) {
        return execCmd(cmd,false,namespace);
    }

    private boolean checkNamespaceExist(String namespaceName) {
        if (null == namespaceName) return true;
        String cmd = "ls /var/run/netns/ |grep " + namespaceName + "$";
        Process process = exeCmdWithNewSh(cmd);
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
    public Process exeCmdWithNewSh(String namespace, String cmd) {
        return execCmd(cmd,true,namespace);
    }

    @Override
    public Process exeCmdWithNewSh(String cmd) {
        return execCmd(cmd,true,Namespace.DEFAULT_NAMESPACE.getName());
    }

    @Override
    public Process exeCmdInDefaultNamespace(String cmd) {
        return execCmd(cmd, false, Namespace.DEFAULT_NAMESPACE.getName());
    }

    @Override
    public Process exeCmdInDefaultNamespaceAndWaitForCloseIOStream(String cmd) {
        return execCmdAndWaitForAndCloseIOSteam(cmd,false,Namespace.DEFAULT_NAMESPACE.getName());
    }


    @Override
    public Process execCmd(String cmd, boolean isCreateNewSh, String namespaceName) {
        if (namespaceName != Namespace.DEFAULT_NAMESPACE.getName()) {
            if (!checkNamespaceExist(namespaceName)) {
                return null;
            } else {
                cmd = "ip netns exec " + namespaceName + " " + cmd;
            }
        }
        Process process = null;
        try {
            if (isCreateNewSh) {
                String[] cmdprep = new String[]{"/bin/sh", "-c", cmd};
                process = runtime.exec(cmdprep);
            } else {
                process = runtime.exec(cmd);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return process;
    }

    @Override
    public Process waitForAndCloseIOSteam(Process process) {
        try (InputStream inputStream = process.getInputStream();
             OutputStream outputStream = process.getOutputStream();
             InputStream errorStream = process.getErrorStream()
        ) {
            process.waitFor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return process;
    }
}
