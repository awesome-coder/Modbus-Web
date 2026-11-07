package com.modbusweb.web.dto;

import java.util.List;
import java.util.Map;

public record MetadataResponse(
        List<Option> protocols,
        List<Option> areas,
        List<Option> dataTypes,
        List<Option> byteOrders,
        List<Option> wordOrders,
        List<Option> accessModes,
        List<Option> stringCharsets,
        List<Option> bacnetObjectTypes,
        List<EndiannessPreset> endiannessPresets,
        Map<String, Object> limits) {}
