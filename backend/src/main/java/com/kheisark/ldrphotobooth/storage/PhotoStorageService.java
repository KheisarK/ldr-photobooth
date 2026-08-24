package com.kheisark.ldrphotobooth.storage;

import com.kheisark.ldrphotobooth.api.ApiException;
import com.kheisark.ldrphotobooth.booth.Participant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PhotoStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final int CELL_WIDTH = 480;
    private static final int CELL_HEIGHT = 360;
    private static final int GAP = 16;
    private static final int MARGIN = 24;

    private final Path uploadRoot;

    public PhotoStorageService(@Value("${app.storage.upload-directory}") String uploadDirectory) {
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public void validatePhotos(List<MultipartFile> photos) {
        if (photos == null || photos.size() != 4) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_PHOTO_COUNT",
                    "Kirim tepat empat foto."
            );
        }

        for (MultipartFile photo : photos) {
            if (photo.isEmpty()) {
                throw invalidPhoto("Foto tidak boleh kosong.");
            }
            if (!ALLOWED_TYPES.contains(photo.getContentType())) {
                throw invalidPhoto("Foto harus berformat JPEG atau PNG.");
            }
            try (InputStream input = photo.getInputStream()) {
                if (ImageIO.read(input) == null) {
                    throw invalidPhoto("Salah satu berkas tidak dapat dibaca sebagai gambar.");
                }
            } catch (IOException exception) {
                throw invalidPhoto("Salah satu foto gagal dibaca.");
            }
        }
    }

    public List<String> savePhotos(String code, Participant participant, List<MultipartFile> photos) {
        Path boothDirectory = safeBoothDirectory(code);
        List<String> savedPaths = new ArrayList<>(4);

        try {
            Files.createDirectories(boothDirectory);
            for (int index = 0; index < photos.size(); index++) {
                MultipartFile photo = photos.get(index);
                String extension = "image/png".equals(photo.getContentType()) ? "png" : "jpg";
                Path destination = boothDirectory
                        .resolve(participant.name().toLowerCase() + "-" + (index + 1) + "." + extension)
                        .normalize();
                ensureInsideRoot(destination);
                try (InputStream input = photo.getInputStream()) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                savedPaths.add(destination.toString());
            }
            return savedPaths;
        } catch (IOException exception) {
            throw storageFailure("Foto gagal disimpan. Silakan coba lagi.", exception);
        }
    }

    public String createPhotostrip(String code, List<String> personAPaths, List<String> personBPaths) {
        if (personAPaths.size() != 4 || personBPaths.size() != 4) {
            throw storageFailure("Photostrip membutuhkan empat foto dari setiap peserta.", null);
        }

        int width = MARGIN * 2 + CELL_WIDTH * 2 + GAP;
        int height = MARGIN * 2 + CELL_HEIGHT * 4 + GAP * 3;
        BufferedImage strip = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = strip.createGraphics();

        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            for (int row = 0; row < 4; row++) {
                int y = MARGIN + row * (CELL_HEIGHT + GAP);
                drawCover(graphics, readImage(personAPaths.get(row)), MARGIN, y);
                drawCover(graphics, readImage(personBPaths.get(row)), MARGIN + CELL_WIDTH + GAP, y);
            }
        } finally {
            graphics.dispose();
        }

        Path result = safeBoothDirectory(code).resolve("result.png").normalize();
        ensureInsideRoot(result);
        try {
            Files.createDirectories(result.getParent());
            if (!ImageIO.write(strip, "png", result.toFile())) {
                throw storageFailure("Pembuat photostrip sedang tidak tersedia.", null);
            }
            return result.toString();
        } catch (IOException exception) {
            throw storageFailure("Photostrip gagal disimpan.", exception);
        }
    }

    public Path resolveResult(String resultPath) {
        Path result = Path.of(resultPath).toAbsolutePath().normalize();
        ensureInsideRoot(result);
        if (!Files.isRegularFile(result)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESULT_NOT_FOUND", "Berkas photostrip tidak ditemukan.");
        }
        return result;
    }

    private BufferedImage readImage(String filePath) {
        try {
            BufferedImage image = ImageIO.read(Path.of(filePath).toFile());
            if (image == null) {
                throw storageFailure("Salah satu foto tersimpan gagal diproses.", null);
            }
            return image;
        } catch (IOException exception) {
            throw storageFailure("Salah satu foto tersimpan gagal dibaca.", exception);
        }
    }

    private void drawCover(Graphics2D graphics, BufferedImage image, int x, int y) {
        double scale = Math.max(
                (double) CELL_WIDTH / image.getWidth(),
                (double) CELL_HEIGHT / image.getHeight()
        );
        int scaledWidth = (int) Math.ceil(image.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(image.getHeight() * scale);
        int offsetX = x + (CELL_WIDTH - scaledWidth) / 2;
        int offsetY = y + (CELL_HEIGHT - scaledHeight) / 2;

        var previousClip = graphics.getClip();
        graphics.clipRect(x, y, CELL_WIDTH, CELL_HEIGHT);
        graphics.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight, null);
        graphics.setClip(previousClip);
    }

    private Path safeBoothDirectory(String code) {
        Path directory = uploadRoot.resolve(code).normalize();
        ensureInsideRoot(directory);
        return directory;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(uploadRoot)) {
            throw storageFailure("Lokasi penyimpanan foto tidak valid.", null);
        }
    }

    private ApiException invalidPhoto(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PHOTO", message);
    }

    private ApiException storageFailure(String message, Exception cause) {
        ApiException exception = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
