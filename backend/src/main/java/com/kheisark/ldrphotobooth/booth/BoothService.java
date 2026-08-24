package com.kheisark.ldrphotobooth.booth;

import com.kheisark.ldrphotobooth.api.ApiException;
import com.kheisark.ldrphotobooth.storage.PhotoStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Service
public class BoothService {

    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;

    private final BoothRepository boothRepository;
    private final BoothPhotoRepository photoRepository;
    private final PhotoStorageService storageService;
    private final SecureRandom random = new SecureRandom();

    public BoothService(
            BoothRepository boothRepository,
            BoothPhotoRepository photoRepository,
            PhotoStorageService storageService
    ) {
        this.boothRepository = boothRepository;
        this.photoRepository = photoRepository;
        this.storageService = storageService;
    }

    @Transactional
    public Booth create(String participantAName, String modeValue) {
        String normalizedName = participantAName == null || participantAName.isBlank()
                ? null
                : participantAName.trim();
        try {
            return boothRepository.save(new Booth(generateUniqueCode(), normalizedName, BoothMode.from(modeValue)));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MODE", exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public BoothSummary get(String code) {
        Booth booth = findBooth(code);
        return summarize(booth);
    }

    @Transactional
    public BoothSummary upload(String code, String participantValue, List<MultipartFile> photos) {
        Booth booth = findBoothForUpdate(code);
        Participant participant = parseParticipant(participantValue);
        ensureCorrectTurn(booth, participant);
        storageService.validatePhotos(photos);

        List<String> savedPaths = storageService.savePhotos(booth.getCode(), participant, photos);
        List<BoothPhoto> savedPhotos = IntStream.range(0, savedPaths.size())
                .mapToObj(index -> new BoothPhoto(booth, participant, index + 1, savedPaths.get(index)))
                .toList();
        photoRepository.saveAll(savedPhotos);

        if (participant == Participant.A) {
            booth.finishParticipantA();
        } else {
            booth.finishParticipantB();
        }

        boothRepository.save(booth);
        return summarize(booth);
    }

    @Transactional(readOnly = true)
    public Path getResult(String code) {
        Booth booth = findBooth(code);
        if (booth.getStatus() != BoothStatus.COMPLETED || booth.getResultPath() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RESULT_NOT_READY",
                    "Photostrip belum siap. Tunggu sampai kedua peserta selesai mengambil foto."
            );
        }
        return storageService.resolveResult(booth.getResultPath());
    }

    @Transactional(readOnly = true)
    public Path getReferencePhoto(String code, int index) {
        Booth booth = findBooth(code);
        if (booth.getMode() != BoothMode.REFERENCE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REFERENCE_DISABLED", "Room ini memakai Surprise Mode.");
        }
        if (index < 1 || index > 4 || booth.getStatus() == BoothStatus.WAITING_A) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REFERENCE_NOT_READY", "Foto referensi belum tersedia.");
        }
        List<BoothPhoto> photos = photoRepository.findAllByBoothAndParticipantOrderByPhotoIndexAsc(booth, Participant.A);
        if (photos.size() < index) throw new ApiException(HttpStatus.NOT_FOUND, "REFERENCE_NOT_READY", "Foto referensi belum tersedia.");
        return storageService.resolvePhoto(photos.get(index - 1).getFilePath());
    }

    @Transactional
    public BoothSummary finalizeBooth(String code, String ownerToken, String frameValue) {
        Booth booth = findBoothForUpdate(code);
        verifyOwner(booth, ownerToken);
        if (booth.getStatus() != BoothStatus.READY_TO_FINALIZE) {
            throw new ApiException(HttpStatus.CONFLICT, "PHOTOS_NOT_READY", "Tunggu sampai kedua orang selesai mengambil foto.");
        }
        FrameStyle frame;
        try {
            frame = FrameStyle.from(frameValue);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FRAME", exception.getMessage());
        }
        List<String> a = pathsFor(booth, Participant.A);
        List<String> b = pathsFor(booth, Participant.B);
        String resultPath = storageService.createPhotostrip(booth.getCode(), a, b, frame);
        booth.complete(resultPath, frame);
        boothRepository.save(booth);
        return summarize(booth);
    }

    @Transactional
    public void delete(String code, String ownerToken) {
        Booth booth = findBoothForUpdate(code);
        verifyOwner(booth, ownerToken);

        storageService.deleteBoothFiles(booth.getCode());
        photoRepository.deleteAllByBooth(booth);
        boothRepository.delete(booth);
    }

    private BoothSummary summarize(Booth booth) {
        long personACount = photoRepository.countByBoothAndParticipant(booth, Participant.A);
        long personBCount = photoRepository.countByBoothAndParticipant(booth, Participant.B);
        Instant expiresAt = booth.getStatus() == BoothStatus.COMPLETED
                ? (booth.getCompletedAt() == null ? booth.getCreatedAt() : booth.getCompletedAt()).plus(Duration.ofMinutes(15))
                : booth.getCreatedAt().plus(Duration.ofHours(24));
        return new BoothSummary(booth.getCode(), booth.getStatus(), booth.getMode(), booth.getFrameStyle(), personACount, personBCount, expiresAt);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanupExpiredRooms() {
        Instant now = Instant.now();
        for (Booth booth : boothRepository.findAll()) {
            Instant expiry = booth.getStatus() == BoothStatus.COMPLETED
                    ? (booth.getCompletedAt() == null ? booth.getCreatedAt() : booth.getCompletedAt()).plus(Duration.ofMinutes(15))
                    : booth.getCreatedAt().plus(Duration.ofHours(24));
            if (expiry.isBefore(now)) deleteInternal(booth);
        }
    }

    private List<String> pathsFor(Booth booth, Participant participant) {
        return photoRepository.findAllByBoothAndParticipantOrderByPhotoIndexAsc(booth, participant)
                .stream().map(BoothPhoto::getFilePath).toList();
    }

    private void verifyOwner(Booth booth, String ownerToken) {
        if (ownerToken == null || booth.getOwnerToken() == null || !booth.getOwnerToken().equals(ownerToken.trim())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_OWNER_TOKEN", "Room hanya dapat dikelola dari perangkat yang membuatnya.");
        }
    }

    private void deleteInternal(Booth booth) {
        storageService.deleteBoothFiles(booth.getCode());
        photoRepository.deleteAllByBooth(booth);
        boothRepository.delete(booth);
    }

    private Booth findBooth(String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return boothRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BOOTH_NOT_FOUND",
                        "Kode booth tidak ditemukan. Periksa kembali kode yang kamu masukkan."
                ));
    }

    private Booth findBoothForUpdate(String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return boothRepository.findByCodeIgnoreCaseForUpdate(normalizedCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BOOTH_NOT_FOUND",
                        "Kode booth tidak ditemukan. Periksa kembali kode yang kamu masukkan."
                ));
    }

    private Participant parseParticipant(String value) {
        try {
            return Participant.from(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PARTICIPANT", exception.getMessage());
        }
    }

    private void ensureCorrectTurn(Booth booth, Participant participant) {
        boolean valid = (participant == Participant.A && booth.getStatus() == BoothStatus.WAITING_A)
                || (participant == Participant.B && booth.getStatus() == BoothStatus.WAITING_B);
        if (!valid) {
            String message = switch (booth.getStatus()) {
                case WAITING_A -> "Peserta B belum bisa mengirim foto sebelum peserta A selesai.";
                case WAITING_B -> "Foto peserta A sudah terkirim. Sekarang giliran peserta B.";
                case READY_TO_FINALIZE, COMPLETED -> "Sesi foto ini sudah selesai dan tidak menerima foto baru.";
            };
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WRONG_PARTICIPANT_TURN",
                    message
            );
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!boothRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CODE_GENERATION_FAILED",
                "Kode booth baru gagal dibuat. Silakan coba lagi."
        );
    }

    public record BoothSummary(String code, BoothStatus status, BoothMode mode, FrameStyle frameStyle, long personACount, long personBCount, Instant expiresAt) {
    }
}
