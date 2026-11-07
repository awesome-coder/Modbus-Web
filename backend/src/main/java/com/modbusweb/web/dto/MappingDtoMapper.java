package com.modbusweb.web.dto;

import com.modbusweb.domain.entity.BacnetMapping;

public final class MappingDtoMapper {

    private MappingDtoMapper() {}

    public static BacnetMappingResponse toResponse(BacnetMapping mapping) {
        if (mapping == null) {
            return null;
        }
        var point = mapping.getPoint();
        var device = point.getDevice();
        return new BacnetMappingResponse(
                mapping.getId(),
                point.getId(),
                point.getCode(),
                point.getName(),
                device.getId(),
                device.getName(),
                mapping.isEnabled(),
                mapping.getObjectType(),
                mapping.getObjectInstance(),
                mapping.objectId(),
                mapping.getObjectName(),
                mapping.getUnits(),
                mapping.getCovIncrement(),
                mapping.getDescription(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt(),
                mapping.getVersion());
    }
}
