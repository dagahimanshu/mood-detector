package com.mooddetector.mood_detector.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SingleImageResult {
    private String filename;
    private Mood primaryMood;
    private String moodLabel;
    private ImageMetrics metrics;
    private int imageWidth;
    private int imageHeight;
    private String error;  // non-null if this particular image failed
}
