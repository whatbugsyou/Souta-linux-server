package com.souta.linuxserver.monitor;

import com.souta.linuxserver.service.NamespaceCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CPUHealthMonitor {

    private final NamespaceCommandService commandService;
    @Autowired
    @Qualifier("monitorPool")
    private ScheduledExecutorService monitorPool;

    public CPUHealthMonitor(NamespaceCommandService commandService) {
        this.commandService = commandService;
    }

    @PostConstruct
    public void monitor() {
        Runnable keepCPUHealth = () -> {
            String cmd = "top -b -n 1 |sed -n '8p'|awk '{print $1,$9,$12}'";
            Process process = commandService.exec(cmd);
            try (InputStream inputStream = process.getInputStream(); OutputStream outputStream = process.getOutputStream(); InputStream errorStream = process.getErrorStream(); InputStreamReader inputStreamReader = new InputStreamReader(inputStream); BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String line;
                Pattern compile = Pattern.compile("(\\d+) (.+) (.+)");
                if ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = compile.matcher(line);
                    if (matcher.matches()) {
                        String pid = matcher.group(1);
                        float cpu = Float.parseFloat(matcher.group(2));
                        String command = matcher.group(3);
                        if (cpu > 100 && command.contains("ss5")) {
                            log.info("CPUHealthMonitor is going to kill pid{}---{}%...", pid, cpu);
                            commandService.execAndWaitForAndCloseIOSteam("kill -9 " + pid);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
//        monitorPool.scheduleAtFixedRate(keepCPUHealth, 0, 60, TimeUnit.SECONDS);
    }
}
