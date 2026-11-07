package com.modbusweb.web;

import com.modbusweb.domain.BacnetObjectType;
import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.ProtocolType;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import com.modbusweb.web.dto.EndiannessPreset;
import com.modbusweb.web.dto.MetadataResponse;
import com.modbusweb.web.dto.Option;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MetadataController {

    @GetMapping("/meta")
    public MetadataResponse meta() {
        return new MetadataResponse(
                Arrays.stream(ProtocolType.values())
                        .map(
                                value ->
                                        new Option(
                                                value.name(),
                                                value.getLabelZh(),
                                                value.getLabelEn(),
                                                Map.of(
                                                        "defaultPort", value.getDefaultPort(),
                                                        "secure", value.isSecure())))
                        .toList(),
                Arrays.stream(PointArea.values())
                        .map(
                                value ->
                                        new Option(
                                                value.name(),
                                                value.getLabelZh(),
                                                value.getLabelEn(),
                                                Map.of(
                                                        "functionCode", value.getFunctionCode(),
                                                        "register", value.isRegister(),
                                                        "writable", value.isWritable(),
                                                        "referenceBase", value.getReferenceBase())))
                        .toList(),
                Arrays.stream(ModbusDataType.values())
                        .map(
                                value ->
                                        new Option(
                                                value.name(),
                                                value.getLabelZh(),
                                                value.getLabelEn(),
                                                Map.of(
                                                        "fixedRegisterCount",
                                                        value.getFixedRegisterCount(),
                                                        "byteSize", value.getByteSize(),
                                                        "numeric", value.isNumeric(),
                                                        "variableLength", value.isVariableLength(),
                                                        "floatingPoint", value.isFloatingPoint())))
                        .toList(),
                Arrays.stream(ByteOrder.values())
                        .map(value -> Option.of(value.name(), value.getLabelZh(), value.getLabelEn()))
                        .toList(),
                Arrays.stream(WordOrder.values())
                        .map(value -> Option.of(value.name(), value.getLabelZh(), value.getLabelEn()))
                        .toList(),
                Arrays.stream(PointAccess.values())
                        .map(value -> Option.of(value.name(), value.getLabelZh(), value.getLabelEn()))
                        .toList(),
                Arrays.stream(StringCharset.values())
                        .map(value -> Option.of(value.name(), value.getLabel(), value.getLabel()))
                        .toList(),
                Arrays.stream(BacnetObjectType.values())
                        .map(
                                value ->
                                        new Option(
                                                value.name(),
                                                value.getLabelZh(),
                                                value.getLabelEn(),
                                                Map.of(
                                                        "typeCode", value.getTypeCode(),
                                                        "family", value.getFamily().name(),
                                                        "input", value.isInput())))
                        .toList(),
                List.of(
                        new EndiannessPreset(
                                "ABCD", "大端 / 高字在前", "Big-endian / high word first",
                                "BIG_ENDIAN", "BIG_ENDIAN", "3F 80 00 00 = 1.0"),
                        new EndiannessPreset(
                                "BADC", "大端 / 字节交换", "Big-endian words / byte swapped",
                                "LITTLE_ENDIAN", "BIG_ENDIAN", "80 3F 00 00 = 1.0"),
                        new EndiannessPreset(
                                "CDAB", "小端 / 字交换", "Little-endian words / word swapped",
                                "BIG_ENDIAN", "LITTLE_ENDIAN", "00 00 3F 80 = 1.0"),
                        new EndiannessPreset(
                                "DCBA", "小端 / 全交换", "Little-endian / fully swapped",
                                "LITTLE_ENDIAN", "LITTLE_ENDIAN", "00 00 80 3F = 1.0")),
                limits());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", "modbus-web");
        body.put("version", "1.0.0");
        body.put("timestamp", Instant.now());
        return body;
    }

    private static Map<String, Object> limits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxReadRegisters", 125);
        limits.put("maxWriteRegisters", 123);
        limits.put("maxReadCoils", 2000);
        limits.put("maxAddress", 65535);
        limits.put("maxPointsToReadAtOnce", 500);
        limits.put("bacnetMaxInstance", 4194302);
        limits.put("bacnetObjectNameMaxLength", 64);
        return limits;
    }
}
