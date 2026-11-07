package com.modbusweb.web.dto;

import com.modbusweb.domain.ProtocolType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
        @NotBlank(message = "设备名称不能为空") @Size(max = 120, message = "设备名称不能超过 120 个字符")
                String name,
        @NotNull(message = "协议不能为空") ProtocolType protocol,
        @NotBlank(message = "IP / 主机名不能为空") @Size(max = 255) String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotNull @Min(0) @Max(255) Integer unitId,
        @Min(100) @Max(60000) Integer connectTimeoutMs,
        @Min(100) @Max(120000) Integer requestTimeoutMs,
        Boolean enabled,
        @Size(max = 1024) String tlsClientCertPath,
        @Size(max = 1024) String tlsClientKeyPath,
        @Size(max = 512) String tlsKeyPassword,
        @Size(max = 1024) String tlsCaCertPath,
        Boolean tlsVerifyHostname,
        @Size(max = 1000) String description) {}
