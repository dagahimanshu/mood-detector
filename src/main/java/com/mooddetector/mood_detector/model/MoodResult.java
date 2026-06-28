package com.mooddetector.mood_detector.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MoodResult {
    private Mood primaryMood;
    private String moodLabel;
    private String moodDescription;
    private double confidence;
    private List<SecondaryMood> secondaryMoods;
    private ImageMetrics metrics;
    private int imageWidth;
    private int imageHeight;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ImageFeatures features;

    @Data
    @Builder
    public static class SecondaryMood {
        private Mood mood;
        private String label;
        private double confidence;
    }
}
