package com.modbusweb.web.dto;

import com.modbusweb.domain.BacnetObjectType;
import java.util.Map;

public record BacnetObjectView(
        Long mappingId,
        Long pointId,
        String pointCode,
        String pointName,
        BacnetObjectType objectType,
        int objectInstance,
        String objectId,
        String objectName,
        String presentValueType,
        Object presentValue,
        String units,
        Double covIncrement,
        boolean enabled,
        Map<String, Object> source) {}
