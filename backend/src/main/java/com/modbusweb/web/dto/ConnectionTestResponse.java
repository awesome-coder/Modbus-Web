package com.modbusweb.web.dto;

public record ConnectionTestResponse(
        boolean success,
        String message,
        long latencyMs,
        boolean secure,
        String resolvedHost,
        int port) {}
