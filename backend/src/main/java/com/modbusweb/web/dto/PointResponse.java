package com.modbusweb.web.dto;

import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import java.time.Instant;

public record PointResponse(
        Long id,
        Long deviceId,
        String deviceName,
        String code,
        String name,
        PointArea area,
        int address,
        String addressReference,
        Integer unitId,
        int effectiveUnitId,
        ModbusDataType dataType,
        ByteOrder byteOrder,
        WordOrder wordOrder,
        Integer bitIndex,
        Integer registerLength,
        int registerCount,
        StringCharset stringCharset,
        double scale,
        double offset,
        Integer decimals,
        String unit,
        PointAccess access,
        boolean enabled,
        String description,
        boolean writable,
        BacnetMappingResponse bacnetMapping,
        Instant createdAt,
        Instant updatedAt,
        Long version) {}
