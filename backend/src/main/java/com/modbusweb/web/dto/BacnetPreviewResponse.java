package com.modbusweb.web.dto;

import java.time.Instant;
import java.util.List;

public record BacnetPreviewResponse(
        Long deviceId,
        String deviceName,
        Instant generatedAt,
        boolean valuesIncluded,
        List<BacnetObjectView> objects,
        List<String> warnings) {}
