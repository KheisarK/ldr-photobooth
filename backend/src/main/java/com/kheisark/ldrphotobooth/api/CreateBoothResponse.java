package com.kheisark.ldrphotobooth.api;

import com.kheisark.ldrphotobooth.booth.BoothStatus;
import com.kheisark.ldrphotobooth.booth.BoothMode;

public record CreateBoothResponse(String code, BoothStatus status, BoothMode mode, String shareUrl, String ownerToken) {
}
