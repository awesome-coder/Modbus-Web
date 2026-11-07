package com.modbusweb.domain;

public enum PointAccess {
    READ_ONLY("只读", "Read only"),
    READ_WRITE("读写", "Read / write"),
    WRITE_ONLY("只写", "Write only");

    private final String labelZh;
    private final String labelEn;

    PointAccess(String labelZh, String labelEn) {
        this.labelZh = labelZh;
        this.labelEn = labelEn;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public boolean canRead() {
        return this != WRITE_ONLY;
    }

    public boolean canWrite() {
        return this != READ_ONLY;
    }
}
