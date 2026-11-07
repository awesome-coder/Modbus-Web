package com.modbusweb.domain;

public enum ByteOrder {
    BIG_ENDIAN("大端字节序 (AB)", "Big-endian bytes (AB)"),
    LITTLE_ENDIAN("小端字节序 (BA)", "Little-endian bytes (BA)");

    private final String labelZh;
    private final String labelEn;

    ByteOrder(String labelZh, String labelEn) {
        this.labelZh = labelZh;
        this.labelEn = labelEn;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public String getLabelEn() {
        return labelEn;
    }
}
