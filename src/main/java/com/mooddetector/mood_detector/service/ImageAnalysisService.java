package com.mooddetector.mood_detector.service;

import com.mooddetector.mood_detector.client.HuggingFaceClient;
import com.mooddetector.mood_detector.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private static final int MAX_SIDE = 512;

    private final HuggingFaceClient huggingFaceClient;

    private static final Map<String, Mood> LABEL_TO_MOOD = Arrays.stream(Mood.values())
            .collect(Collectors.toMap(m -> m.getLabel().toLowerCase(), m -> m));

    private static final String FEATURE_QUESTION =
            "Describe the location, time of day, season, what is happening, " +
            "the theme or feeling, the dominant colors, and the main subjects or objects.";

    // ── Public API ──────────────────────────────────────────────────────

    public MoodResult analyze(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }

        BufferedImage image = decodeImage(file);
        BufferedImage sampled = downsample(image);
        ImageMetrics metrics = computeMetrics(sampled);

        if (huggingFaceClient.isConfigured()) {
            try {
                String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
                return analyzeWithVLM(file.getBytes(), mimeType, metrics, image.getWidth(), image.getHeight());
            } catch (Exception e) {
                log.warn("HuggingFace analysis failed, falling back to pixel-based: {}", e.getMessage());
            }
        }

        log.info("Using pixel-based analysis");
        return classifyFromPixels(metrics, image.getWidth(), image.getHeight());
    }

    public MoodResult analyzeMultiple(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one image file is required");
        }

        List<ImageMetrics> allMetrics = new ArrayList<>();
        MultipartFile firstValid = null;
        int combinedWidth = 0;
        int combinedHeight = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                log.warn("Skipping empty file in batch");
                continue;
            }
            if (firstValid == null) firstValid = file;

            BufferedImage image = decodeImage(file);
            BufferedImage sampled = downsample(image);
            allMetrics.add(computeMetrics(sampled));

            combinedWidth = Math.max(combinedWidth, image.getWidth());
            combinedHeight = Math.max(combinedHeight, image.getHeight());
        }

        if (allMetrics.isEmpty()) {
            throw new IllegalArgumentException("No valid images could be processed");
        }

        ImageMetrics combined = aggregateMetrics(allMetrics);

        if (huggingFaceClient.isConfigured() && firstValid != null) {
            try {
                String mimeType = firstValid.getContentType() != null ? firstValid.getContentType() : "image/jpeg";
                return analyzeWithVLM(firstValid.getBytes(), mimeType, combined, combinedWidth, combinedHeight);
            } catch (Exception e) {
                log.warn("HuggingFace batch analysis failed, falling back to pixel-based: {}", e.getMessage());
            }
        }

        log.info("Using pixel-based analysis for {} images", allMetrics.size());
        return classifyFromPixels(combined, combinedWidth, combinedHeight);
    }

    // ── VLM flow (single API call) ─────────────────────────────────────

    private MoodResult analyzeWithVLM(
            byte[] imageBytes, String mimeType, ImageMetrics metrics, int width, int height) throws IOException {

        HuggingFaceClient.AnalysisResult analysis = huggingFaceClient.analyzeImage(imageBytes, mimeType);
        log.info("VLM analysis: mood={}, confidence={}, caption={}",
                analysis.primaryMood(), analysis.confidence(), analysis.caption());

        Mood primary = LABEL_TO_MOOD.getOrDefault(analysis.primaryMood().toLowerCase(), Mood.NEUTRAL);
        double confidence = Math.max(0.0, Math.min(1.0, analysis.confidence()));

        List<MoodResult.SecondaryMood> secondary = new ArrayList<>();
        if (!analysis.secondaryMood().isBlank()) {
            Mood sec = LABEL_TO_MOOD.get(analysis.secondaryMood().toLowerCase());
            if (sec != null && sec != primary) {
                secondary.add(MoodResult.SecondaryMood.builder()
                        .mood(sec)
                        .label(sec.getLabel())
                        .confidence(round(confidence * 0.6))
                        .build());
            }
        }

        ImageFeatures features = ImageFeatures.builder()
                .caption(analysis.caption())
                .details(analysis.details())
                .build();

        return MoodResult.builder()
                .primaryMood(primary)
                .moodLabel(primary.getLabel())
                .moodDescription(primary.getDescription())
                .confidence(round(confidence))
                .secondaryMoods(secondary)
                .metrics(metrics)
                .imageWidth(width)
                .imageHeight(height)
                .features(features)
                .build();
    }

    // ── Pixel-based flow (fallback) ─────────────────────────────────────

    private MoodResult classifyFromPixels(ImageMetrics m, int origW, int origH) {
        Map<Mood, Double> scores = new EnumMap<>(Mood.class);
        for (Mood mood : Mood.values()) scores.put(mood, scoreMood(mood, m));

        List<Map.Entry<Mood, Double>> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<Mood, Double>comparingByValue().reversed())
                .toList();

        double totalScore = ranked.stream().mapToDouble(Map.Entry::getValue).sum();
        Map.Entry<Mood, Double> best = ranked.get(0);
        double confidence = Math.min(1.0,
                (totalScore > 0 ? best.getValue() / totalScore : 0.5) * Mood.values().length * 0.6);

        List<MoodResult.SecondaryMood> secondary = new ArrayList<>();
        for (int i = 1; i < Math.min(3, ranked.size()); i++) {
            var e = ranked.get(i);
            double sc = Math.min(1.0,
                    (totalScore > 0 ? e.getValue() / totalScore : 0) * Mood.values().length * 0.6);
            if (sc > 0.05) secondary.add(MoodResult.SecondaryMood.builder()
                    .mood(e.getKey()).label(e.getKey().getLabel()).confidence(round(sc)).build());
        }

        Mood primary = best.getKey();
        return MoodResult.builder()
                .primaryMood(primary)
                .moodLabel(primary.getLabel())
                .moodDescription(primary.getDescription())
                .confidence(round(confidence))
                .secondaryMoods(secondary)
                .metrics(m)
                .imageWidth(origW)
                .imageHeight(origH)
                .build();
    }

    // ── Image decoding & metrics ────────────────────────────────────────

    private BufferedImage decodeImage(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) throw new IllegalArgumentException(
                    "Unsupported or corrupted image: " + file.getOriginalFilename());
            return img;
        }
    }

    private BufferedImage downsample(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= MAX_SIDE && h <= MAX_SIDE) return src;
        double scale = (double) MAX_SIDE / Math.max(w, h);
        int nw = (int) (w * scale), nh = (int) (h * scale);
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        dst.getGraphics().drawImage(
                src.getScaledInstance(nw, nh, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
        return dst;
    }

    private ImageMetrics computeMetrics(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long total = (long) w * h;
        double sumLum = 0, sumSat = 0, sumWarmth = 0;
        double[] lums = new double[w * h];
        int idx = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double r = ((rgb >> 16) & 0xFF) / 255.0;
                double g = ((rgb >> 8) & 0xFF) / 255.0;
                double b = (rgb & 0xFF) / 255.0;

                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                lums[idx++] = lum;
                sumLum += lum;

                double maxC = Math.max(r, Math.max(g, b));
                double minC = Math.min(r, Math.min(g, b));
                sumSat += (maxC > 0) ? (maxC - minC) / maxC : 0;

                sumWarmth += (r - b);
            }
        }

        double brightness = sumLum / total;
        double saturation = sumSat / total;
        double warmth = Math.max(-1.0, Math.min(1.0, sumWarmth / total));

        double sqDiff = 0;
        for (double l : lums) {
            double d = l - brightness;
            sqDiff += d * d;
        }
        double contrast = Math.min(1.0, Math.sqrt(sqDiff / total) * 2.0);

        return ImageMetrics.builder()
                .brightness(round(brightness))
                .contrast(round(contrast))
                .saturation(round(saturation))
                .warmth(round(Math.max(-1.0, Math.min(1.0, warmth))))
                .pixelCount(total)
                .build();
    }

    private ImageMetrics aggregateMetrics(List<ImageMetrics> metricsList) {
        double totalPixels = metricsList.stream()
                .mapToLong(ImageMetrics::getPixelCount)
                .sum();

        double weightedBrightness = 0, weightedContrast = 0;
        double weightedSaturation = 0, weightedWarmth = 0;

        for (ImageMetrics m : metricsList) {
            double weight = m.getPixelCount() / totalPixels;
            weightedBrightness += weight * m.getBrightness();
            weightedContrast += weight * m.getContrast();
            weightedSaturation += weight * m.getSaturation();
            weightedWarmth += weight * m.getWarmth();
        }

        return ImageMetrics.builder()
                .brightness(round(weightedBrightness))
                .contrast(round(weightedContrast))
                .saturation(round(weightedSaturation))
                .warmth(round(Math.max(-1.0, Math.min(1.0, weightedWarmth))))
                .pixelCount((long) totalPixels)
                .build();
    }

    // ── Pixel-based mood scoring ────────────────────────────────────────

    private double scoreMood(Mood mood, ImageMetrics m) {
        return switch (mood) {
            case VIBRANT -> score(m, 0.75, 1.5, 0.60, 1.0, 0.85, 2.5, 0.40, 0.8);
            case ENERGETIC -> score(m, 0.65, 1.2, 0.70, 1.5, 0.65, 1.5, 0.20, 0.5);
            case WARM -> score(m, 0.65, 1.5, 0.35, 0.8, 0.55, 1.2, 0.60, 2.5);
            case ROMANTIC -> score(m, 0.60, 1.3, 0.30, 0.8, 0.50, 1.5, 0.50, 2.0);
            case NOSTALGIC -> score(m, 0.65, 1.5, 0.30, 0.8, 0.40, 1.2, 0.45, 1.8);
            case SERENE -> score(m, 0.65, 1.5, 0.25, 1.2, 0.40, 1.2, 0.10, 1.0);
            case DREAMY -> score(m, 0.75, 1.5, 0.20, 1.5, 0.30, 1.5, 0.10, 0.8);
            case COOL -> score(m, 0.55, 1.2, 0.40, 1.0, 0.45, 1.2, -0.40, 2.0);
            case MELANCHOLIC -> score(m, 0.35, 1.5, 0.35, 1.2, 0.25, 1.5, -0.20, 1.5);
            case DRAMATIC -> score(m, 0.40, 1.0, 0.80, 3.0, 0.55, 1.2, 0.10, 0.5);
            case MYSTERIOUS -> score(m, 0.25, 1.5, 0.60, 1.5, 0.40, 1.2, -0.10, 0.8);
            case GLOOMY -> score(m, 0.20, 2.0, 0.40, 1.0, 0.15, 1.5, -0.30, 1.5);
            case NEUTRAL -> score(m, 0.50, 1.0, 0.35, 1.0, 0.30, 1.0, 0.00, 1.0);
        };
    }

    private double score(ImageMetrics m,
                         double iB, double wB, double iC, double wC,
                         double iS, double wS, double iW, double wW) {
        double nW = (m.getWarmth() + 1.0) / 2.0;
        double niW = (iW + 1.0) / 2.0;
        return Math.max(0, wB * (1 - Math.abs(m.getBrightness() - iB)))
                + Math.max(0, wC * (1 - Math.abs(m.getContrast() - iC)))
                + Math.max(0, wS * (1 - Math.abs(m.getSaturation() - iS)))
                + Math.max(0, wW * (1 - Math.abs(nW - niW)));
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
