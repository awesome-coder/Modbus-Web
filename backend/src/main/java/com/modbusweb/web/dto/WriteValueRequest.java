package com.modbusweb.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteValueRequest(
        @NotBlank(message = "写入值不能为空") @Size(max = 2048) String value) {}
