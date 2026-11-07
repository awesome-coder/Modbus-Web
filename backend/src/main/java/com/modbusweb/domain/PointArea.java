package com.modbusweb.domain;

/**
 * Modbus data areas. The {@code referenceBase} is the classic 5-digit Modbus data-model reference
 * base, used only for display and Excel conversion. Wire addresses are always zero-based.
 */
public enum PointArea {
    COIL(1, "0x", false, true, 1, "线圈", "Coil"),
    DISCRETE_INPUT(2, "1x", false, false, 10001, "离散输入", "Discrete Input"),
    HOLDING_REGISTER(3, "4x", true, true, 40001, "保持寄存器", "Holding Register"),
    INPUT_REGISTER(4, "3x", true, false, 30001, "输入寄存器", "Input Register");

    private final int functionCode;
    private final String prefix;
    private final boolean register;
    private final boolean writable;
    private final int referenceBase;
    private final String labelZh;
    private final String labelEn;

    PointArea(
            int functionCode,
            String prefix,
            boolean register,
            boolean writable,
            int referenceBase,
            String labelZh,
            String labelEn) {
        this.functionCode = functionCode;
        this.prefix = prefix;
        this.register = register;
        this.writable = writable;
        this.referenceBase = referenceBase;
        this.labelZh = labelZh;
        this.labelEn = labelEn;
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isRegister() {
        return register;
    }

    public boolean isWritable() {
        return writable;
    }

    public int getReferenceBase() {
        return referenceBase;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public String getLabelEn() {
        return labelEn;
    }

    /** Convert a zero-based protocol address to a 5-digit human readable reference. */
    public String toReference(int address) {
        return String.format("%05d", referenceBase + address);
    }
}
