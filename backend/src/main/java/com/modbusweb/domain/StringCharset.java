package com.modbusweb.domain;

public enum StringCharset {
    ASCII("ASCII"),
    UTF_8("UTF-8"),
    UTF_16BE("UTF-16BE"),
    UTF_16LE("UTF-16LE");

    private final String label;

    StringCharset(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
