package com.souta.linuxserver.service;

public interface NamespaceCommandService extends CommandService {

    Process exec(String command, String namespaceName);

    default Process execAndWaitForAndCloseIOSteam(String cmd, String namespaceName) {
        Process process = exec(cmd, namespaceName);
        waitForAndCloseIOSteam(process);
        return process;
    }
}
