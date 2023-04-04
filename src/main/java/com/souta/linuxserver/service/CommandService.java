package com.souta.linuxserver.service;

public interface CommandService {

    /**
     * @param namespace --  namespace name
     * @param cmd       --  bash command
     * @return Process
     */
    Process exeCmdInNamespace(String namespace, String cmd);


    Process exeCmdWithNewSh(String namespace, String cmd);

    Process exeCmdWithNewSh(String cmd);

    /**
     * @param cmd --  bash command
     * @return Process
     */
    Process exeCmdInDefaultNamespace(String cmd);

    Process exeCmdInDefaultNamespaceAndCloseIOStream(String cmd);


    Process execCmd(String cmd, boolean isCreateNewSh, String namespaceName);

    Process waitForAndCloseIOSteam(Process process);

    default Process execCmdAndWaitForAndCloseIOSteam(String cmd, boolean isCreateNewSh, String namespaceName) {
        Process process = execCmd(cmd, isCreateNewSh, namespaceName);
        waitForAndCloseIOSteam(process);
        return process;
    }
}
