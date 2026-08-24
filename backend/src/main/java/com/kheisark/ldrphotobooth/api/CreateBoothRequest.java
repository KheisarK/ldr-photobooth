package com.kheisark.ldrphotobooth.api;

import jakarta.validation.constraints.Size;

public record CreateBoothRequest(
        @Size(max = 80, message = "Nama maksimal 80 karakter.")
        String name,
        String mode
) {
}
