package com.modbusweb.domain;

public enum ProtocolType {
    MODBUS_TCP("Modbus TCP", "Modbus TCP", 502, false),
    MODBUS_TCP_SECURE("Modbus TCP Security (TLS)", "Modbus TCP Security (TLS)", 802, true);

    private final String labelZh;
    private final String labelEn;
    private final int defaultPort;
    private final boolean secure;

    ProtocolType(String labelZh, String labelEn, int defaultPort, boolean secure) {
        this.labelZh = labelZh;
        this.labelEn = labelEn;
        this.defaultPort = defaultPort;
        this.secure = secure;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public boolean isSecure() {
        return secure;
    }
}
