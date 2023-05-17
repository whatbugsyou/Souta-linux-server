package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Namespace;

public interface NamespaceCommandService extends CommandService {

    Process exec(String command, String namespaceName);

    default Process execAndWaitForAndCloseIOSteam(String cmd) {
        return execAndWaitForAndCloseIOSteam(cmd, Namespace.DEFAULT_NAMESPACE.getName());
    }

    default Process execAndWaitForAndCloseIOSteam(String cmd, String namespaceName) {
        Process process = exec(cmd, namespaceName);
        waitForAndCloseIOSteam(process);
        return process;
    }
}
