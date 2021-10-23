package com.souta.linuxserver.service.impl;

import com.souta.linuxserver.dto.Line;
import com.souta.linuxserver.dto.Socks5InfoDTO;
import com.souta.linuxserver.service.LineService;
import com.souta.linuxserver.service.Socks5Service;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LineServiceImpl implements LineService {
    private final Socks5Service socks5Service;

    public LineServiceImpl(Socks5Service socks5Service) {
        this.socks5Service = socks5Service;
    }

    @Override
    public List<Line> getAllLines() {
        List<Socks5InfoDTO> socks5InfoDTOList = socks5Service.getAllSocks5();
        ArrayList<Line> lines = new ArrayList<>();
        for (Socks5InfoDTO socks5InfoDTO : socks5InfoDTOList) {
            Line line = new Line();
            line.setSocks5Info(socks5InfoDTO);
            lines.add(line);
        }
        return lines;
    }
}
