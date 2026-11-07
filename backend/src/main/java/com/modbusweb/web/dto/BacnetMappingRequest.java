package com.modbusweb.web.dto;

import com.modbusweb.domain.BacnetObjectType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BacnetMappingRequest(
        @NotNull(message = "点位不能为空") Long pointId,
        Boolean enabled,
        @NotNull(message = "BACnet 对象类型不能为空") BacnetObjectType objectType,
        @NotNull(message = "BACnet 对象实例号不能为空")
                @Min(0)
                @Max(4194302)
                Integer objectInstance,
        @NotBlank(message = "BACnet 对象名称不能为空") @Size(max = 160) String objectName,
        @Size(max = 64) String units,
        @Min(0) Double covIncrement,
        @Size(max = 1000) String description) {}
