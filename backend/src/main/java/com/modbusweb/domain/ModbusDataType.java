package com.modbusweb.domain;

public enum ModbusDataType {
    BOOL("BOOL", "布尔", "Boolean", 0, 0, false, false),
    INT16("INT16", "有符号 16 位整数", "Signed 16-bit integer", 1, 2, true, true),
    UINT16("UINT16", "无符号 16 位整数", "Unsigned 16-bit integer", 1, 2, true, false),
    INT32("INT32", "有符号 32 位整数", "Signed 32-bit integer", 2, 4, true, true),
    UINT32("UINT32", "无符号 32 位整数", "Unsigned 32-bit integer", 2, 4, true, false),
    INT64("INT64", "有符号 64 位整数", "Signed 64-bit integer", 4, 8, true, true),
    UINT64("UINT64", "无符号 64 位整数", "Unsigned 64-bit integer", 4, 8, true, false),
    FLOAT32("FLOAT32", "32 位浮点数", "32-bit float (IEEE 754)", 2, 4, true, true),
    FLOAT64("FLOAT64", "64 位浮点数", "64-bit float (IEEE 754)", 4, 8, true, true),
    STRING("STRING", "字符串", "String", 0, 0, false, false),
    HEX("HEX", "原始十六进制", "Raw hexadecimal", 0, 0, false, false);

    private final String code;
    private final String labelZh;
    private final String labelEn;
    private final int fixedRegisterCount;
    private final int byteSize;
    private final boolean numeric;
    private final boolean signed;

    ModbusDataType(
            String code,
            String labelZh,
            String labelEn,
            int fixedRegisterCount,
            int byteSize,
            boolean numeric,
            boolean signed) {
        this.code = code;
        this.labelZh = labelZh;
        this.labelEn = labelEn;
        this.fixedRegisterCount = fixedRegisterCount;
        this.byteSize = byteSize;
        this.numeric = numeric;
        this.signed = signed;
    }

    public String getCode() {
        return code;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public int getFixedRegisterCount() {
        return fixedRegisterCount;
    }

    public int getByteSize() {
        return byteSize;
    }

    public boolean isNumeric() {
        return numeric;
    }

    public boolean isSigned() {
        return signed;
    }

    public boolean isVariableLength() {
        return this == STRING || this == HEX;
    }

    public boolean isFloatingPoint() {
        return this == FLOAT32 || this == FLOAT64;
    }

    public boolean isInteger() {
        return numeric && !isFloatingPoint();
    }
}
