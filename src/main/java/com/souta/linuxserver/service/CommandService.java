package com.souta.linuxserver.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CommandService {

    Process exec(String cmd);

    default Process execAndWaitForAndCloseIOSteam(String cmd) {
        return execAndWaitForAndCloseIOSteam(cmd);
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
