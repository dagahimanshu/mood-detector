package com.mooddetector.mood_detector.controller;

import com.mooddetector.mood_detector.model.MoodResult;
import com.mooddetector.mood_detector.service.ImageAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mood")
@RequiredArgsConstructor
public class ImageMoodController {

    private final ImageAnalysisService analysisService;

    @PostMapping(value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MoodResult> analyze(@RequestPart("image") MultipartFile image) {
        log.info("Received: {} ({} bytes)", image.getOriginalFilename(), image.getSize());
        try {
            return ResponseEntity.ok(analysisService.analyze(image));
        } catch (IllegalArgumentException e) {
            log.warn("Bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("Processing failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/analyze/combined",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MoodResult> analyzeCombined(
            @RequestPart("images") List<MultipartFile> images) {

        log.info("Received combined request: {} files", images.size());

        if (images.size() < 2) {
            log.warn("Combined endpoint called with fewer than 2 images");
            return ResponseEntity.badRequest().build();
        }

        if (images.size() > 10) {
            log.warn("Combined endpoint called with more than 10 images");
            return ResponseEntity.badRequest().build();
        }

        try {
            MoodResult result = analysisService.analyzeMultiple(images);
            log.info("Combined mood: {} (confidence={}, images={})",
                    result.getMoodLabel(), result.getConfidence(), images.size());
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("Bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (IOException e) {
            log.error("Failed to process combined images", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "mood-detector"));
    }
}
