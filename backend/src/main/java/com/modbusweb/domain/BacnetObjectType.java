package com.modbusweb.domain;

/**
 * Subset of standard BACnet object types useful for Modbus point mapping.
 *
 * <p>The numeric type codes follow ASHRAE 135. This enum is intentionally an integration boundary:
 * an external BACnet stack can use {@link #getTypeCode()} and {@link #getObjectId(int)} directly.
 */
public enum BacnetObjectType {
    ANALOG_INPUT(0, "analog-input", ValueFamily.ANALOG, "模拟量输入", "Analog Input"),
    ANALOG_OUTPUT(1, "analog-output", ValueFamily.ANALOG, "模拟量输出", "Analog Output"),
    ANALOG_VALUE(2, "analog-value", ValueFamily.ANALOG, "模拟量值", "Analog Value"),
    BINARY_INPUT(3, "binary-input", ValueFamily.BINARY, "二进制输入", "Binary Input"),
    BINARY_OUTPUT(4, "binary-output", ValueFamily.BINARY, "二进制输出", "Binary Output"),
    BINARY_VALUE(5, "binary-value", ValueFamily.BINARY, "二进制值", "Binary Value"),
    MULTI_STATE_INPUT(13, "multi-state-input", ValueFamily.MULTI_STATE, "多态输入", "Multi-state Input"),
    MULTI_STATE_OUTPUT(14, "multi-state-output", ValueFamily.MULTI_STATE, "多态输出", "Multi-state Output"),
    MULTI_STATE_VALUE(19, "multi-state-value", ValueFamily.MULTI_STATE, "多态值", "Multi-state Value");

    public enum ValueFamily {
        ANALOG,
        BINARY,
        MULTI_STATE
    }

    private final int typeCode;
    private final String objectTypeName;
    private final ValueFamily family;
    private final String labelZh;
    private final String labelEn;

    BacnetObjectType(
            int typeCode,
            String objectTypeName,
            ValueFamily family,
            String labelZh,
            String labelEn) {
        this.typeCode = typeCode;
        this.objectTypeName = objectTypeName;
        this.family = family;
        this.labelZh = labelZh;
        this.labelEn = labelEn;
    }

    public int getTypeCode() {
        return typeCode;
    }

    public String getObjectTypeName() {
        return objectTypeName;
    }

    public ValueFamily getFamily() {
        return family;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public boolean isInput() {
        return this == ANALOG_INPUT || this == BINARY_INPUT || this == MULTI_STATE_INPUT;
    }

    /** BACnet object identifier in the conventional {@code type,instance} notation. */
    public String getObjectId(int instance) {
        return objectTypeName + "," + instance;
    }
}
