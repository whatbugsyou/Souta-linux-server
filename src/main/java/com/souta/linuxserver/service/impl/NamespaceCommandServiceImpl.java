package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.NamespaceCommandService;
import org.springframework.stereotype.Service;

@Service
public class NamespaceCommandServiceImpl extends CommandServiceImpl implements NamespaceCommandService {

    private String wrappedCommandWithNamespace(String command, String namespaceName) {
        if (namespaceName != Namespace.DEFAULT_NAMESPACE.getName()) {
            command = "ip netns exec " + namespaceName + " " + command;
        }
        return command;
    }

    @Override
    public Process exec(String command, String namespaceName) {
        command = wrappedCommandWithNamespace(command, namespaceName);
        return exec(command);
    }

}
