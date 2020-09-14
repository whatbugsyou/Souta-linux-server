package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Line;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.FutureTask;

public interface LineService {
    Set<String> dialingLines = new CopyOnWriteArraySet();
    int lineRedialWait = 2;

    FutureTask<Line> createLine(String lineId);
    Line getLine(String lineId);
    FutureTask<Line> refreshLine(String lineId);
    boolean deleteLine(String lineId);
    boolean editProtoInLine(String lineId, String protoId, String action);
    boolean checkExits(String lineId);

}
