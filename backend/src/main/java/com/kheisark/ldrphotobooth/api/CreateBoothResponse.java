package com.kheisark.ldrphotobooth.api;

import com.kheisark.ldrphotobooth.booth.BoothStatus;

public record CreateBoothResponse(String code, BoothStatus status, String shareUrl) {
}
