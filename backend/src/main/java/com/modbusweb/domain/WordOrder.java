package com.modbusweb.domain;

public enum WordOrder {
    BIG_ENDIAN("高字在前 (AB CD)", "High word first (AB CD)"),
    LITTLE_ENDIAN("低字在前 (CD AB)", "Low word first (CD AB)");

    private final String labelZh;
    private final String labelEn;

    WordOrder(String labelZh, String labelEn) {
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
