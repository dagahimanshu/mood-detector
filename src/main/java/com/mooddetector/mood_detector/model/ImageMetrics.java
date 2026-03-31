package com.mooddetector.mood_detector.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageMetrics {
    private double brightness;   // [0, 1]  perceptual luminance
    private double contrast;     // [0, 1]  std dev of luminance
    private double saturation;   // [0, 1]  average HSV saturation
    private double warmth;       // [-1, 1] warm=positive, cool=negative
    private long pixelCount;
}
