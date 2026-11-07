package com.modbusweb.web.dto;

import com.modbusweb.domain.ProtocolType;
import java.time.Instant;

public record DeviceStatusResponse(
        Long deviceId,
        String deviceName,
        ProtocolType protocol,
        boolean connected,
        Instant connectedAt,
        String lastError,
        long reconnectCount,
        long requestCount,
        long errorCount) {}
