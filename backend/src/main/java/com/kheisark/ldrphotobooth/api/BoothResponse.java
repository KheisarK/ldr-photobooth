package com.kheisark.ldrphotobooth.api;

import com.kheisark.ldrphotobooth.booth.BoothStatus;

public record BoothResponse(
        String code,
        BoothStatus status,
        PhotoCounts photoCounts,
        String resultUrl
) {
    public record PhotoCounts(long a, long b) {
    }
}
