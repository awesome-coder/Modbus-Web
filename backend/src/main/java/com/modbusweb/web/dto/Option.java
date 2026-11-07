package com.modbusweb.web.dto;

import java.util.Map;

public record Option(String value, String labelZh, String labelEn, Map<String, Object> extra) {

    public static Option of(String value, String labelZh, String labelEn) {
        return new Option(value, labelZh, labelEn, Map.of());
    }
}
