package com.modbusweb.web.dto;

import com.modbusweb.domain.BacnetObjectType;
import java.time.Instant;

public record BacnetMappingResponse(
        Long id,
        Long pointId,
        String pointCode,
        String pointName,
        Long deviceId,
        String deviceName,
        boolean enabled,
        BacnetObjectType objectType,
        int objectInstance,
        String objectId,
        String objectName,
        String units,
        Double covIncrement,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Long version) {}
