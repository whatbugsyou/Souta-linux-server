package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Line;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.FutureTask;

public interface LineService {
    Set<String> dialingLines = new CopyOnWriteArraySet();
    /**
     * seconds of a gap between two dials with the same id.
     */
    int lineRedialWait = 2;

    /**
     * @param lineId
     * @return FutureTask.gets line if success ,otherwise null.
     */
    FutureTask<Line> createLine(String lineId);

    /**
     *
     * @param lineId
     * @return Line with started socks information,otherwise null.
     */
    Line getLine(String lineId);
    FutureTask<Line> refreshLine(String lineId);
    boolean deleteLine(String lineId);

    /**
     *
     * @param lineId
     * @param protoId socks5 or shadowsocks
     * @param action on or off
     * @return true if it is dial up.
     */
    boolean editProtoInLine(String lineId, String protoId, String action);
    boolean checkExits(String lineId);

}
