package com.souta.linuxserver.service;

import com.souta.linuxserver.dto.Line;

import java.util.List;

public interface LineService {
    List<Line> getAllLines();
}
