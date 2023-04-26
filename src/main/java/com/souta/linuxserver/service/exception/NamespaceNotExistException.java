package com.souta.linuxserver.service.exception;

public class NamespaceNotExistException extends Exception {
    public NamespaceNotExistException(String message) {
        super("NamespaceNotExist:" + message);
    }
}
