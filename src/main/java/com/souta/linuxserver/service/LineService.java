package com.souta.linuxserver.service;

import com.souta.linuxserver.line.Line;

import java.util.List;
import java.util.Set;

public interface LineService {


    /**
     * @param lineId
     * @return FutureTask.gets line if success ,otherwise null.
     */
    Line createLine(String lineId);

    /**
     * @param lineId
     * @return Line with started socks information,otherwise null.
     */
    Line getLine(String lineId);

    List<Line> getLines(Set<String> lineIdList);

    List<Line> getAvailableLines();

    Line refresh(String lineId);

    boolean deleteLine(String lineId);

    /**
     * @param lineId
     * @param protoId socks5 or shadowsocks
     * @param action  on or off
     * @return true if it is dial up.
     */
    boolean editProtoInLine(String lineId, String protoId, String action);
}
