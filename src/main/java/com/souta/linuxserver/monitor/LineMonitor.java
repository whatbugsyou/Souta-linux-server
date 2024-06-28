package com.souta.linuxserver.monitor;

import com.souta.linuxserver.adsl.ADSLConfigManager;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.line.Line;
import com.souta.linuxserver.line.LineSender;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.PPPOEService;
import com.souta.linuxserver.service.RateLimitService;
import com.souta.linuxserver.util.LineMax;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Component
@Slf4j
public class LineMonitor {

    public static final Set<String> dialingLines = new CopyOnWriteArraySet();
    public static final Set<String> deadLineIdSet = new CopyOnWriteArraySet<>();
    public static final Set<Line> errorSendLines = new HashSet<>();
    public static final Set<Line> deadLineToSend = new HashSet<>();
    /**
     * record the times of false dial ,if the times are >= checkingTimesOfDefineDeadLine will send to java server as a dead line
     * lineId:dialFalseTimes
     */
    public static final ConcurrentHashMap<String, Integer> dialFalseTimesMap = new ConcurrentHashMap<>();
    public static final int checkingTimesOfDefineDeadLine = 3;

    private final RateLimitService rateLimitService;
    private final LineSender lineSender;
    /**
     * the line that is error sent is going to be resent.
     */

    private final PPPOEService pppoeService;
    private final LineService lineService;
    private final HostConfig hostConfig;
    private final ADSLConfigManager adslConfigManager;
    @Autowired
    @Qualifier("basePool")
    private ExecutorService basePool;

    @Autowired
    @Qualifier("monitorPool")
    private ScheduledExecutorService monitorPool;

    public LineMonitor(RateLimitService rateLimitService, LineSender lineSender, PPPOEService pppoeService, LineService lineService, HostConfig hostConfig, ADSLConfigManager adslConfigManager) {
        this.rateLimitService = rateLimitService;
        this.lineSender = lineSender;
        this.pppoeService = pppoeService;
        this.lineService = lineService;
        this.hostConfig = hostConfig;
        this.adslConfigManager = adslConfigManager;
    }


    /**
     * @return a number about the max number of not dial-up line numbers
     */
    public String generateLineID() {
        LineMax lineMax = new LineMax();
        HashSet<String> dialuppedIdSet = pppoeService.getDialuppedIdSet();
        Set<String> excludeSet;
        excludeSet = dialuppedIdSet;
        excludeSet.addAll(dialingLines);
        excludeSet.addAll(deadLineIdSet);
        int count = adslConfigManager.count();
        if (excludeSet.size() < count) {
            for (String id : excludeSet) {
                lineMax.add(Integer.parseInt(id));
            }
            return String.valueOf(lineMax.getMax());
        }
        return null;
    }

    /**
     * monitor deadline : redial false with 3 times will be defined as a deadline .it is recorded in redialCheckMap.check redialCheckMap for deadline and send it to java server.
     * monitor fullDial : if dial-upped lines count is less than the count of server configured ,it will call addOneDial to make full use of server line resources.
     * monitor errorLine: if error occurs in sending  line info,it will resend when  errorSendLines is checked once a period of 30s.
     */

    @PostConstruct
    private void monitorLines() {
        Runnable checkDeadLine = () -> {
            if (!deadLineToSend.isEmpty()) {
                lineSender.sendDeadLines(new ArrayList<>(deadLineToSend));
            }
        };
        Runnable checkErrorSendLines = () -> {
            if (!errorSendLines.isEmpty()) {
                lineSender.sendLinesInfo(new ArrayList<>(errorSendLines));
            }
        };

        Runnable checkRateLimit = () -> {
            if (hostConfig.getHost().getVersion() == 1) {
                HashSet<String> dialuppedIdSet = pppoeService.getDialuppedIdSet();
                Set<String> limitedLineIdSet = rateLimitService.getLimitedLineIdSet();
                dialuppedIdSet.removeAll(limitedLineIdSet);
                Set<String> notLimitedIdSet = dialuppedIdSet;
                notLimitedIdSet.forEach(rateLimitService::limit);
            }
        };
        // TODO batched create
        Runnable checkFullDial = () -> {
            String lineID = generateLineID();
            if (lineID != null) {
                boolean addTrue = dialingLines.add(lineID);
                if (addTrue) {
                    basePool.execute(() -> {
                        log.info("LineMonitor is going to create line{}...", lineID);
                        Line line = lineService.createLine(lineID);
                        dialingLines.remove(lineID);
                        lineSender.sendLineInfo(line);
                    });
                }
            }
        };
        log.info("monitorLines starting...");
        monitorPool.scheduleAtFixedRate(checkErrorSendLines, 0, 30, TimeUnit.SECONDS);
        monitorPool.scheduleAtFixedRate(checkFullDial, 0, 10, TimeUnit.MILLISECONDS);
        monitorPool.scheduleAtFixedRate(checkDeadLine, 0, 30, TimeUnit.SECONDS);
        monitorPool.scheduleAtFixedRate(checkRateLimit, 0, 60, TimeUnit.SECONDS);
    }

}
