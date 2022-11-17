package com.souta.linuxserver.service;

public interface RateLimitService {
    boolean limit(String lineId, Integer maxKBPerSec);
}
