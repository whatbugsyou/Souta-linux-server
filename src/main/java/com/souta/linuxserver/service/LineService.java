package com.souta.linuxserver.service;

import com.souta.linuxserver.entity.Line;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.FutureTask;

public interface LineService {
    /**
     * Using thread safe set to record the dialingLines
     * can avoid recording the same Line repeatedly
     * and the Line will exist in this set forever ,
     * which makes the line out of full dial monitoring
     * when the line is shutdown by the ISP.
     * <p>
     * Although the issue above happens in an extreme low possibility,
     * we still make sure to run stably
     */
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
     * @param lineId
     * @return Line with started socks information,otherwise null.
     */
    Line getLine(String lineId);

    List<Line> getLines(Set<String> lineIdList);

    FutureTask<Line> refreshLine(String lineId);

    boolean deleteLine(String lineId);

    /**
     * @param lineId
     * @param protoId socks5 or shadowsocks
     * @param action  on or off
     * @return true if it is dial up.
     */
    boolean editProtoInLine(String lineId, String protoId, String action);

    boolean checkExits(String lineId);

    /**
     * @return a number about the max number of not dial-up line numbers
     */
    String generateLineID();
}
