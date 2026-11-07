package com.modbusweb.web.dto;

public record EndiannessPreset(
        String value,
        String labelZh,
        String labelEn,
        String byteOrder,
        String wordOrder,
        String example) {}
