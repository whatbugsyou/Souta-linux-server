package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.souta.linuxserver.dto.Socks5Info;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.Socks5Service;
import com.souta.linuxserver.util.FileUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

@RestController
@RequestMapping("/v1.0/line/socks5")
public class Socks5Controller {
    private final Socks5Service socks5Service;

    public Socks5Controller(Socks5Service socks5Service) {
        this.socks5Service = socks5Service;
    }

    @PostConstruct()
    public void init() {
        Object socks5Info = getSocks5Info("");
        try {
            HttpResponse execute = HttpRequest
                    .post(Host.java_server_host + "/v1.0/line/socks5")
                    .body(JSON.toJSONString(socks5Info), "application/json;charset=UTF-8")
                    .execute();
            if (execute.getStatus() != 200) {
                throw new ResponseNotOkException("error in sending registerHost from java server,API(PUT) :  /v1.0/server");
            }
        } catch (ResponseNotOkException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @GetMapping("info")
    public Object getSocks5Info(@RequestParam(value = "ip", defaultValue = "") String ip) {
        if (ip.equals("")) {
            return socks5Service.getAllSocks5();
        } else {
            return socks5Service.getSocks5(ip);
        }
    }

    @PutMapping("/config")
    public void changeConfig(@RequestBody Socks5Info socks5Info) {
        socks5Service.updateConfig(socks5Info);
    }


}
