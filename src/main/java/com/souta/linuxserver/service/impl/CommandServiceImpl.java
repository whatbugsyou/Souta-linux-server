package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.entity.Namespace;
import com.souta.linuxserver.service.CommandService;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CommandServiceImpl implements CommandService {

    private static final String[] notSupportSymbols = {"|", "<", ">"};
    private final Runtime runtime = Runtime.getRuntime();

    private boolean isSupported(String command) {
        for (String notSupportSymbol : notSupportSymbols) {
            if (command.contains(notSupportSymbol)) {
                return false;
            }
        }
        return true;
    }

    private String[] wrappedCommandWithNewSh(String command) {
        return new String[]{"/bin/sh", "-c", command};
    }

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

    @Override
    public Process exec(String command) {
        Process process = null;
        try {
            if (isSupported(command)) {
                String[] commandArray = wrappedCommandWithNewSh(command);
                process = runtime.exec(commandArray);
            } else {
                process = runtime.exec(command);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return process;
    }
}
