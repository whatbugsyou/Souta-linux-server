package com.souta.linuxserver.service;

import java.util.Set;

public interface RateLimitService {
    boolean limit(String lineId, Integer maxKBPerSec);

    boolean limit(String lineId);

    void removeAll();

    Set<String> getLimitedLineIdSet();
}
