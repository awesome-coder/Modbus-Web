package com.modbusweb.web;

public class ModbusOperationException extends RuntimeException {

    public ModbusOperationException(String message) {
        super(message);
    }

    public ModbusOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
