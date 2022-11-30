package com.souta.linuxserver.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.util.FileUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

@Data
@ConfigurationProperties(prefix = "host")
@Slf4j
public class HostConfig {
    private String javaServerHost;
    private String filePath;
    private Integer defaultRateLimitKB;
    private Host host;

    @Data
    public static class Host {
        private String id;
        private String ip;
        private String name;
        private String location;
        private String port;
        private String operator;
        private Integer version;
        private Integer limit;
        private Integer rateLimitKB;
    }

    @PostConstruct
    public void init() {
        try {
            readHostFromConfigFile();
            if (host.rateLimitKB == null) {
                host.rateLimitKB = defaultRateLimitKB;
            }
            if (host.id == null) {
                registerHost();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            System.exit(1);
        }
    }

    private void readHostFromConfigFile() throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException(filePath);
        }
        String jsonStr = FileUtil.ReadFile(filePath);
        host = JSON.parseObject(jsonStr, Host.class);
    }

    private void registerHost() throws Exception {
        String jsonStr = JSONObject.toJSONString(host);
        HttpResponse execute = HttpRequest
                .put(javaServerHost + "/v1.0/server")
                .body(jsonStr, "application/json;charset=UTF-8")
                .execute();
        if (execute.getStatus() != 200) {
            throw new ResponseNotOkException("error in sending registerHost from java server,API(PUT) :  /v1.0/server");
        }
        String responseBody = execute.body();
        JSONObject response = JSON.parseObject(responseBody);
        Object id = response.get("id");
        if (id == null) {
            throw new NullPointerException("id is null");
        } else {
            host.id = String.valueOf(id);
        }
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(filePath);
            fileWriter.write(JSONObject.toJSONString(host));
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
