package com.kheisark.ldrphotobooth.api;

import com.kheisark.ldrphotobooth.booth.Booth;
import com.kheisark.ldrphotobooth.booth.BoothService;
import com.kheisark.ldrphotobooth.booth.BoothStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/booths")
public class BoothController {

    private final BoothService boothService;
    private final String frontendUrl;

    public BoothController(
            BoothService boothService,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.boothService = boothService;
        this.frontendUrl = frontendUrl.trim().replaceAll("/+$", "");
    }

    @PostMapping
    ResponseEntity<CreateBoothResponse> create(
            @Valid @RequestBody(required = false) CreateBoothRequest request
    ) {
        Booth booth = boothService.create(request == null ? null : request.name(), request == null ? null : request.mode());
        CreateBoothResponse response = new CreateBoothResponse(
                booth.getCode(),
                booth.getStatus(),
                booth.getMode(),
                frontendUrl + "/booths/" + booth.getCode(),
                booth.getOwnerToken()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}")
    BoothResponse get(@PathVariable String code) {
        return toResponse(boothService.get(code));
    }

    @PostMapping("/{code}/finalize")
    BoothResponse finalizeBooth(
            @PathVariable String code,
            @RequestHeader("X-Booth-Owner-Token") String ownerToken,
            @RequestBody FinalizeBoothRequest request
    ) {
        return toResponse(boothService.finalizeBooth(code, ownerToken, request == null ? null : request.frame()));
    }

    @GetMapping("/{code}/reference/{index}")
    ResponseEntity<Resource> reference(@PathVariable String code, @PathVariable int index) {
        Path photo = boothService.getReferencePhoto(code, index);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(new FileSystemResource(photo));
    }

    @DeleteMapping("/{code}")
    ResponseEntity<Void> delete(
            @PathVariable String code,
            @RequestHeader("X-Booth-Owner-Token") String ownerToken
    ) {
        boothService.delete(code, ownerToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{code}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BoothResponse upload(
            @PathVariable String code,
            @RequestParam String participant,
            @RequestParam("photos") List<MultipartFile> photos
    ) {
        return toResponse(boothService.upload(code, participant, photos));
    }

    @GetMapping(path = "/{code}/result", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<Resource> result(
            @PathVariable String code,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        Path result = boothService.getResult(code);
        ContentDisposition disposition = (download
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename("ldr-photobooth-" + code.toUpperCase() + ".png")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(result));
    }

    private BoothResponse toResponse(BoothService.BoothSummary summary) {
        String resultUrl = summary.status() == BoothStatus.COMPLETED
                ? "/api/booths/" + summary.code() + "/result"
                : null;
        return new BoothResponse(
                summary.code(),
                summary.status(),
                summary.mode(),
                summary.frameStyle(),
                new BoothResponse.PhotoCounts(summary.personACount(), summary.personBCount()),
                resultUrl,
                summary.expiresAt()
        );
    }
}
