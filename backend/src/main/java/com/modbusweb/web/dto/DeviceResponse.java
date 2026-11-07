package com.modbusweb.web.dto;

import com.modbusweb.domain.ProtocolType;
import java.time.Instant;

public record DeviceResponse(
        Long id,
        String name,
        ProtocolType protocol,
        String host,
        int port,
        int unitId,
        int connectTimeoutMs,
        int requestTimeoutMs,
        boolean enabled,
        String tlsClientCertPath,
        String tlsClientKeyPath,
        boolean tlsKeyPasswordSet,
        String tlsCaCertPath,
        boolean tlsVerifyHostname,
        String description,
        long pointCount,
        Instant createdAt,
        Instant updatedAt,
        Long version) {}
