package com.kheisark.ldrphotobooth.booth;

public enum FrameStyle {
    CLASSIC,
    POLAROID,
    MIDNIGHT;

    public static FrameStyle from(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Frame harus CLASSIC, POLAROID, atau MIDNIGHT.");
        }
    }
}
