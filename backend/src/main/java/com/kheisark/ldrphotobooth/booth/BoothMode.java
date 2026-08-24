package com.kheisark.ldrphotobooth.booth;

public enum BoothMode {
    REFERENCE,
    SURPRISE;

    public static BoothMode from(String value) {
        if (value == null || value.isBlank()) return REFERENCE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Mode harus REFERENCE atau SURPRISE.");
        }
    }
}
