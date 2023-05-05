package com.souta.linuxserver.line;


public interface LineBuilder {
    Line build(String lineId);
    Line getLine(String lineId);
}
