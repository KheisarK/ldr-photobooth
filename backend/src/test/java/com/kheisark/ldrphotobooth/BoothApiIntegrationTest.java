package com.kheisark.ldrphotobooth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kheisark.ldrphotobooth.booth.BoothPhotoRepository;
import com.kheisark.ldrphotobooth.booth.BoothRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ldr-photobooth-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.upload-directory=target/test-uploads",
        "app.frontend-url=https://frontend.example",
        "app.cors.allowed-origins=https://preview.example"
})
@AutoConfigureMockMvc
class BoothApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BoothPhotoRepository photoRepository;

    @Autowired
    private BoothRepository boothRepository;

    @BeforeEach
    void cleanDatabase() {
        photoRepository.deleteAll();
        boothRepository.deleteAll();
    }

    @Test
    void completesTheTwoPersonFlowAndDownloadsPhotostrip() throws Exception {
        String code = createBooth();

        mockMvc.perform(photoUpload(code, "a", Color.ORANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_B"))
                .andExpect(jsonPath("$.photoCounts.a").value(4))
                .andExpect(jsonPath("$.photoCounts.b").value(0))
                .andExpect(jsonPath("$.resultUrl").isEmpty());

        mockMvc.perform(get("/api/booths/{code}/reference/{index}", code, 1))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(photoUpload(code, "b", Color.CYAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_FINALIZE"))
                .andExpect(jsonPath("$.photoCounts.a").value(4))
                .andExpect(jsonPath("$.photoCounts.b").value(4))
                .andExpect(jsonPath("$.resultUrl").isEmpty());

        String ownerToken = boothRepository.findByCodeIgnoreCase(code).orElseThrow().getOwnerToken();
        mockMvc.perform(post("/api/booths/{code}/finalize", code)
                        .header("X-Booth-Owner-Token", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frame\":\"POLAROID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.frameStyle").value("POLAROID"))
                .andExpect(jsonPath("$.resultUrl").value("/api/booths/" + code + "/result"));

        mockMvc.perform(get("/api/booths/{code}/result", code))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", matchesPattern("inline;.*\\.png.*")))
                .andExpect(result -> {
                    if (result.getResponse().getContentAsByteArray().length == 0) {
                        throw new AssertionError("Expected a non-empty photostrip.");
                    }
                });

        mockMvc.perform(get("/api/booths/{code}/result?download=true", code))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", matchesPattern("attachment;.*\\.png.*")));
    }

    @Test
    void rejectsWrongTurnAndWrongPhotoCount() throws Exception {
        String code = createBooth();

        mockMvc.perform(photoUpload(code, "b", Color.BLUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WRONG_PARTICIPANT_TURN"));

        mockMvc.perform(multipart("/api/booths/{code}/photos", code)
                        .file(photo("photos", Color.RED))
                        .param("participant", "a"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_PHOTO_COUNT"));
    }

    @Test
    void rejectsRepeatedUploadAndResultBeforeCompletionInIndonesian() throws Exception {
        String code = createBooth();

        mockMvc.perform(get("/api/booths/{code}/result", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESULT_NOT_READY"))
                .andExpect(jsonPath("$.error.message").value(
                        "Photostrip belum siap. Tunggu sampai kedua peserta selesai mengambil foto."
                ));

        mockMvc.perform(photoUpload(code, "a", Color.ORANGE))
                .andExpect(status().isOk());

        mockMvc.perform(photoUpload(code, "a", Color.ORANGE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WRONG_PARTICIPANT_TURN"))
                .andExpect(jsonPath("$.error.message").value(
                        "Foto peserta A sudah terkirim. Sekarang giliran peserta B."
                ));
    }

    @Test
    void allowsCorsFromProductionLocalhostAndConfiguredPreview() throws Exception {
        assertCorsAllowed("https://frontend.example");
        assertCorsAllowed("http://localhost:5173");
        assertCorsAllowed("http://127.0.0.1:5173");
        assertCorsAllowed("https://preview.example");
    }

    @Test
    void returnsNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(get("/api/booths/NOPE99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOTH_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(
                        "Kode booth tidak ditemukan. Periksa kembali kode yang kamu masukkan."
                ));
    }

    @Test
    void onlyCreatorTokenCanDeleteBooth() throws Exception {
        String json = mockMvc.perform(post("/api/booths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(json);
        String code = response.get("code").asText();
        String ownerToken = response.get("ownerToken").asText();

        mockMvc.perform(delete("/api/booths/{code}", code)
                        .header("X-Booth-Owner-Token", "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INVALID_OWNER_TOKEN"));

        mockMvc.perform(delete("/api/booths/{code}", code)
                        .header("X-Booth-Owner-Token", ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/booths/{code}", code))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesIndonesianApiStatus() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aktif"))
                .andExpect(jsonPath("$.message").value(
                        "Backend aktif. Buka aplikasi frontend untuk mulai berfoto."
                ));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("LDR Photobooth API"));
    }

    private void assertCorsAllowed(String origin) throws Exception {
        mockMvc.perform(options("/api/booths")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    private String createBooth() throws Exception {
        String json = mockMvc.perform(post("/api/booths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kei\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("[2-9A-HJ-NP-Z]{6}")))
                .andExpect(jsonPath("$.status").value("WAITING_A"))
                .andExpect(jsonPath("$.shareUrl").value(matchesPattern(".*/booths/[2-9A-HJ-NP-Z]{6}")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(json);
        return response.get("code").asText();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder photoUpload(
            String code,
            String participant,
            Color color
    ) throws Exception {
        var request = multipart("/api/booths/{code}/photos", code);
        request.param("participant", participant);
        for (int index = 0; index < 4; index++) {
            request.file(photo("photos", color));
        }
        return request;
    }

    private MockMultipartFile photo(String fieldName, Color color) throws Exception {
        BufferedImage image = new BufferedImage(24, 18, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(fieldName, "photo.png", "image/png", output.toByteArray());
    }
}
