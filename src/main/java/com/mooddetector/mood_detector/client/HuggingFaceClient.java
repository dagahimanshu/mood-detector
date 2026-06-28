package com.mooddetector.mood_detector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Slf4j
@Component
public class HuggingFaceClient {

    private static final String ROUTER_URL = "https://router.huggingface.co/v1/chat/completions";

    private static final String ANALYSIS_PROMPT = """
            Analyze this image and return a JSON object with these fields:
            - "caption": a one-sentence description of the image
            - "details": describe the location/setting, time of day, season, what is happening, the theme, dominant colors, and main subjects
            - "primaryMood": pick exactly one from [vibrant, energetic, warm, romantic, nostalgic, serene, dreamy, cool, melancholic, dramatic, mysterious, gloomy, neutral]
            - "confidence": your confidence in the mood choice from 0.0 to 1.0
            - "secondaryMood": pick one different mood from the same list that also fits

            Return ONLY valid JSON, no markdown fences, no explanation.""";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiToken;
    private final String visionModel;

    public HuggingFaceClient(
            @Value("${huggingface.api-token:}") String apiToken,
            @Value("${huggingface.vision-model:meta-llama/Llama-3.2-11B-Vision-Instruct}") String visionModel) {
        this.apiToken = apiToken;
        this.visionModel = visionModel;
    }

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }

    /**
     * Sends an image to a VLM and gets back structured analysis (caption, features, mood)
     * in a single API call.
     */
    public AnalysisResult analyzeImage(byte[] imageData, String mimeType) throws IOException {
        String base64Image = Base64.getEncoder().encodeToString(imageData);
        String dataUri = "data:" + mimeType + ";base64," + base64Image;

        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUri)
        );
        Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", ANALYSIS_PROMPT
        );

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(imageContent, textContent)
        );

        Map<String, Object> body = Map.of(
                "model", visionModel,
                "messages", List.of(message),
                "max_tokens", 400
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        RequestEntity<Map<String, Object>> request = RequestEntity
                .post(URI.create(ROUTER_URL))
                .headers(headers)
                .body(body);

        log.debug("Calling VLM: {} via router", visionModel);
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText();

        if (content == null || content.isBlank()) {
            throw new IOException("Empty response from VLM");
        }

        log.debug("VLM raw response: {}", content);
        return parseAnalysis(content);
    }

    private AnalysisResult parseAnalysis(String content) throws IOException {
        String cleaned = content.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        try {
            JsonNode json = objectMapper.readTree(cleaned);

            return new AnalysisResult(
                    json.path("caption").asText(""),
                    json.path("details").asText(""),
                    json.path("primaryMood").asText("neutral"),
                    json.path("confidence").asDouble(0.5),
                    json.path("secondaryMood").asText("")
            );
        } catch (Exception e) {
            log.warn("Failed to parse VLM JSON, using raw content as caption: {}", e.getMessage());
            return new AnalysisResult(cleaned, "", "neutral", 0.5, "");
        }
    }

    public record AnalysisResult(
            String caption,
            String details,
            String primaryMood,
            double confidence,
            String secondaryMood
    ) {}
}
