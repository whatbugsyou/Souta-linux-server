package com.souta.linuxserver.service.exception;

public class EthernetNotExistException extends Exception {
    public EthernetNotExistException(String message) {
        super("EthernetNotExist:" + message);
    }
}
