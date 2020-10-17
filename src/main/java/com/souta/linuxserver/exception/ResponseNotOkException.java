package com.souta.linuxserver.exception;

public class ResponseNotOkException extends Exception {

    public ResponseNotOkException() {
        super();
    }

    public ResponseNotOkException(String message) {
        super(message);
    }
}
