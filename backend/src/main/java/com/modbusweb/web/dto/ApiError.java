package com.modbusweb.web.dto;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Object details,
        String path) {}
