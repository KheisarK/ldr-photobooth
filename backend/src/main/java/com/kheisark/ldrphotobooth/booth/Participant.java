package com.kheisark.ldrphotobooth.booth;

import java.util.Locale;

public enum Participant {
    A,
    B;

    public static Participant from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Peserta wajib diisi.");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Peserta harus diisi dengan 'a' atau 'b'.");
        }
    }
}
