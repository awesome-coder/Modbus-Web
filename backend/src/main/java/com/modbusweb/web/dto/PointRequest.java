package com.modbusweb.web.dto;

import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PointRequest(
        @NotBlank(message = "点位编码不能为空")
                @Size(max = 64)
                @Pattern(
                        regexp = "^[A-Za-z0-9_.:-]+$",
                        message = "点位编码只能包含字母、数字、下划线、点、冒号、横线")
                String code,
        @NotBlank(message = "点位名称不能为空") @Size(max = 160) String name,
        @NotNull(message = "区域不能为空") PointArea area,
        @NotNull @Min(0) @Max(65535) Integer address,
        @Min(0) @Max(255) Integer unitId,
        @NotNull(message = "数据类型不能为空") ModbusDataType dataType,
        @NotNull ByteOrder byteOrder,
        @NotNull WordOrder wordOrder,
        @Min(0) @Max(15) Integer bitIndex,
        @Min(1) @Max(125) Integer registerLength,
        StringCharset stringCharset,
        @NotNull @DecimalMin(value = "-1e12") @DecimalMax(value = "1e12") Double scale,
        @NotNull @DecimalMin(value = "-1e12") @DecimalMax(value = "1e12") Double offset,
        @Min(0) @Max(12) Integer decimals,
        @Size(max = 32) String unit,
        @NotNull PointAccess access,
        Boolean enabled,
        @Size(max = 1000) String description) {}
