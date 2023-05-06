package com.souta.linuxserver.line;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.adsl.ADSL;
import com.souta.linuxserver.config.HostConfig;
import com.souta.linuxserver.entity.DeadLine;
import com.souta.linuxserver.entity.LineDTO;
import com.souta.linuxserver.entity.Shadowsocks;
import com.souta.linuxserver.entity.Socks5;
import com.souta.linuxserver.entity.abs.Socks;
import com.souta.linuxserver.exception.ResponseNotOkException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.souta.linuxserver.monitor.LineMonitor.deadLineToSend;
import static com.souta.linuxserver.monitor.LineMonitor.errorSendLines;

@Component
@Slf4j
public class LineSender {

    private final HostConfig hostConfig;
    @Autowired
    @Qualifier("netPool")
    private ExecutorService netPool;

    public LineSender(HostConfig hostConfig) {
        this.hostConfig = hostConfig;
    }

    /**
     * send lines to the Java server. If response status is not OK or catch an exception, will add lines into errorSendLines, ready checking thread to invoke resend.
     *
     * @param lines
     */
    public void sendLinesInfo(ArrayList<Line> lines) {
        if (!lines.isEmpty()) {
            HashMap<String, Object> data = new HashMap<>();
            data.put("hostId", hostConfig.getHost().getId());
            data.put("lines", wrap(lines));
            String body = new JSONObject(data).toJSONString();
            Runnable runnable = () -> {
                log.info("send Lines Info ...");
                try (HttpResponse response = HttpRequest.put(hostConfig.getJavaServerHost() + "/v1.0/line").body(body).timeout(5000).execute()) {
                    boolean status = response.isOk();
                    if (status) {
                        log.info("send Lines Info ok : {}", body);
                        lines.forEach(errorSendLines::remove);
                    } else {
                        throw new ResponseNotOkException("response not OK in sendLinesInfo to the Java server, API(PUT): /v1.0/line, " + response.body());
                    }
                } catch (RuntimeException | ResponseNotOkException e) {
                    log.error(e.getMessage());
                    log.info("send Lines Info NOT ok : {}", body);
                    lines.forEach(errorSendLines::remove);
                    errorSendLines.addAll(lines);
                }
            };
            netPool.submit(runnable);
        }
    }
    public void sendDeadLines(ArrayList<Line> lines){
        lines.forEach(line -> {
            HashMap<String, Object> data = new HashMap<>();
            DeadLine deadLine = new DeadLine();
            deadLine.setLineId(line.getLineId());
            ADSL adsl = line.getAdsl();
            deadLine.setAdslUser(adsl.getAdslUser());
            deadLine.setAdslPassword(adsl.getAdslPassword());
            data.put("hostId", hostConfig.getHost().getId());
            data.put("deadLine", deadLine);
            String body = new JSONObject(data).toJSONString();
            Runnable runnable = () -> {
                log.info("send deadLine Info : {}", body);
                try (HttpResponse response = HttpRequest.post(hostConfig.getJavaServerHost() + "/v1.0/deadLine").body(body).execute()) {
                    int status = response.getStatus();
                    if (status != 200) {
                        throw new ResponseNotOkException("error in sending dead line info to the java server,API(POST) :  /v1.0/deadLine");
                    }
                    deadLineToSend.remove(line.getLineId());
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            };
            netPool.execute(runnable);
        });
    }

    private List<LineDTO> wrap(List<Line> lines) {
        return lines.stream().map(this::wrap).collect(Collectors.toList());
    }

    public void sendLineInfo(Line line) {
        if (line != null && line.getOutIpAddr() != null) {
            ArrayList<Line> list = new ArrayList<>();
            list.add(line);
            sendLinesInfo(list);
        }
    }

    private LineDTO wrap(Line line) {
        Socks5 socks5 = null;
        Shadowsocks shadowsocks = null;
        for (Socks proxyServer : line.getProxyServers()) {
            proxyServer.setIp(line.getOutIpAddr());
            if (proxyServer instanceof Socks5) {
                socks5 = (Socks5) proxyServer;
            }
            if (proxyServer instanceof Shadowsocks) {
                shadowsocks = (Shadowsocks) proxyServer;
            }
        }
        LineDTO result = new LineDTO(line.getLineId(), socks5, shadowsocks, line.getAdsl().getAdslUser(), line.getAdsl().getAdslPassword());
        return result;
    }
}
