package com.souta.linuxserver.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.souta.linuxserver.dto.Line;
import com.souta.linuxserver.exception.ResponseNotOkException;
import com.souta.linuxserver.service.LineService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.List;

@RestController
@RequestMapping("/v1.0/line")
public class LineController {

    public static final String java_server_host = "http://91vpn.cc";
    private final LineService lineService;

    public LineController(LineService lineService) {
        this.lineService = lineService;
    }


    @PostConstruct()
    public void init() {
        List<Line> allLines = lineService.getAllLines();
        try {
            HttpResponse execute = HttpRequest
                    .post(java_server_host + "/v1.0/line")
                    .body(JSON.toJSONString(allLines), "application/json;charset=UTF-8")
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
}
