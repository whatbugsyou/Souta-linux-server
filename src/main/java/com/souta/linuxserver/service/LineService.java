package com.souta.linuxserver.service;

import com.souta.linuxserver.line.Line;

import java.util.List;
import java.util.Set;

public interface LineService {

    String DEFAULT_LISTEN_IP = "0.0.0.0";
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
