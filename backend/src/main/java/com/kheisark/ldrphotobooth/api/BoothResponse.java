package com.kheisark.ldrphotobooth.api;

import com.kheisark.ldrphotobooth.booth.BoothStatus;
import com.kheisark.ldrphotobooth.booth.BoothMode;
import com.kheisark.ldrphotobooth.booth.FrameStyle;
import java.time.Instant;

public record BoothResponse(
        String code,
        BoothStatus status,
        BoothMode mode,
        FrameStyle frameStyle,
        PhotoCounts photoCounts,
        String resultUrl,
        Instant expiresAt
) {
    public record PhotoCounts(long a, long b) {
    }
}
