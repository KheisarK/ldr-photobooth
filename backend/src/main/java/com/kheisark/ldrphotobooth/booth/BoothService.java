package com.kheisark.ldrphotobooth.booth;

import com.kheisark.ldrphotobooth.api.ApiException;
import com.kheisark.ldrphotobooth.storage.PhotoStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
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
    public Booth create(String participantAName) {
        String normalizedName = participantAName == null || participantAName.isBlank()
                ? null
                : participantAName.trim();
        return boothRepository.save(new Booth(generateUniqueCode(), normalizedName));
    }

    @Transactional(readOnly = true)
    public BoothSummary get(String code) {
        Booth booth = findBooth(code);
        return summarize(booth);
    }

    @Transactional
    public BoothSummary upload(String code, String participantValue, List<MultipartFile> photos) {
        Booth booth = findBooth(code);
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
            List<String> personAPaths = photoRepository
                    .findAllByBoothAndParticipantOrderByPhotoIndexAsc(booth, Participant.A)
                    .stream()
                    .map(BoothPhoto::getFilePath)
                    .toList();
            String resultPath = storageService.createPhotostrip(booth.getCode(), personAPaths, savedPaths);
            booth.complete(resultPath);
        }

        boothRepository.save(booth);
        return summarize(booth);
    }

    @Transactional(readOnly = true)
    public Path getResult(String code) {
        Booth booth = findBooth(code);
        if (booth.getStatus() != BoothStatus.COMPLETED || booth.getResultPath() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESULT_NOT_READY", "Photostrip is not ready yet.");
        }
        return storageService.resolveResult(booth.getResultPath());
    }

    private BoothSummary summarize(Booth booth) {
        long personACount = photoRepository.countByBoothAndParticipant(booth, Participant.A);
        long personBCount = photoRepository.countByBoothAndParticipant(booth, Participant.B);
        return new BoothSummary(booth.getCode(), booth.getStatus(), personACount, personBCount);
    }

    private Booth findBooth(String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return boothRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BOOTH_NOT_FOUND",
                        "Booth code was not found."
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
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WRONG_PARTICIPANT_TURN",
                    "This participant cannot submit photos in the booth's current state."
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
                "A unique booth code could not be generated."
        );
    }

    public record BoothSummary(String code, BoothStatus status, long personACount, long personBCount) {
    }
}
