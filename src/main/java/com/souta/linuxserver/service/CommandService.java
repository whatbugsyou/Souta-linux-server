package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Namespace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CommandService {

    Process exec(String cmd);

    Process exec(String command, String namespaceName);

    default Process execAndWaitForAndCloseIOSteam(String cmd) {
        return execAndWaitForAndCloseIOSteam(cmd, Namespace.DEFAULT_NAMESPACE.getName());
    }

    default Process execAndWaitForAndCloseIOSteam(String cmd, String namespaceName) {
        Process process = exec(cmd, namespaceName);
        waitForAndCloseIOSteam(process);
        return process;
    }

    default Process waitForAndCloseIOSteam(Process process) {
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
