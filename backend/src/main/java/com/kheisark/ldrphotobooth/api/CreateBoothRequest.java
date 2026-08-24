package com.kheisark.ldrphotobooth.api;

import jakarta.validation.constraints.Size;

public record CreateBoothRequest(
        @Size(max = 80, message = "Name must be 80 characters or fewer.")
        String name
) {
}
