package com.modbusweb.web.dto;

import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.ValueQuality;
import java.time.Instant;

public record PointReadResponse(
        Long pointId,
        String pointCode,
        String pointName,
        PointArea area,
        int address,
        String addressReference,
        ModbusDataType dataType,
        boolean success,
        String value,
        Double numericValue,
        Boolean booleanValue,
        String rawHex,
        ValueQuality quality,
        String error,
        String unit,
        int registerCount,
        Instant timestamp,
        long elapsedMs) {}
