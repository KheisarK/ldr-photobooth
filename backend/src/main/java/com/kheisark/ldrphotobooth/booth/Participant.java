package com.kheisark.ldrphotobooth.booth;

import java.util.Locale;

public enum Participant {
    A,
    B;

    public static Participant from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Participant is required.");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Participant must be 'a' or 'b'.");
        }
    }
}
