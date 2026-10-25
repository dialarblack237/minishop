package com.example.minishop.service;

public enum DlqReason {
    EXHAUSTED_RETRIES("exhausted-retries"),
    SCHEMA_MISMATCH("schema-mismatch"),
    BUSINESS_REJECTED("business-rejected"),
    UNKNOWN("unknown");

    private final String value;

    DlqReason(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DlqReason fromValue(String s) {
        for (DlqReason r : values()) {
            if (r.value.equals(s)) return r;
        }
        return UNKNOWN;
    }
}