package com.kheisark.ldrphotobooth.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StatusController {

    @GetMapping({"/", "/api"})
    Map<String, Object> status() {
        return Map.of(
                "service", "LDR Photobooth API",
                "status", "aktif",
                "message", "Backend aktif. Buka aplikasi frontend untuk mulai berfoto.",
                "endpoints", Map.of(
                        "buatRoom", "POST /api/booths",
                        "cekRoom", "GET /api/booths/{code}",
                        "unggahFoto", "POST /api/booths/{code}/photos",
                        "hasil", "GET /api/booths/{code}/result"
                )
        );
    }
}
