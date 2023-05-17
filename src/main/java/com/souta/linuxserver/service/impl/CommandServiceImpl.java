package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.service.CommandService;

import java.io.IOException;

public class CommandServiceImpl implements CommandService {
    private final Runtime runtime = Runtime.getRuntime();
    private static final String[] notSupportSymbols = {"|", "<", ">"};

    @Override
    public Process exec(String command) {
        Process process = null;
        try {
            if (!isSupported(command)) {
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
}
