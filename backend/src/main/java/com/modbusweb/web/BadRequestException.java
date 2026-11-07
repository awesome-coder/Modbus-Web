package com.modbusweb.web;

import java.util.Map;

public class BadRequestException extends RuntimeException {

    private final Object details;

    public BadRequestException(String message) {
        this(message, null);
    }

    public BadRequestException(String message, Object details) {
        super(message);
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }

    public static BadRequestException withField(String field, String message) {
        return new BadRequestException("请求参数校验失败", Map.of(field, message));
    }
}
