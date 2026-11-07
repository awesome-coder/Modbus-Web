package com.modbusweb.web.dto;

import java.time.Instant;
import java.util.List;

public record ReadAllResponse(
        Long deviceId,
        String deviceName,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int total,
        int successCount,
        int errorCount,
        List<PointReadResponse> results) {}
