package com.mooddetector.mood_detector.model;

public enum Mood {
    VIBRANT("Vibrant", "Bold, energetic, and full of life"),
    ENERGETIC("Energetic", "Dynamic, lively, and stimulating"),
    WARM("Warm", "Inviting, comfortable, and cozy"),
    ROMANTIC("Romantic", "Soft, warm, and intimate"),
    NOSTALGIC("Nostalgic", "Golden, hazy, and sentimental"),
    SERENE("Serene", "Peaceful, tranquil, and balanced"),
    DREAMY("Dreamy", "Soft, hazy, and otherworldly"),
    COOL("Cool", "Crisp, refreshing, and composed"),
    MELANCHOLIC("Melancholic", "Subdued, reflective, and wistful"),
    DRAMATIC("Dramatic", "High-contrast, intense, and striking"),
    MYSTERIOUS("Mysterious", "Dark, moody, and ambiguous"),
    GLOOMY("Gloomy", "Low-light, heavy, and somber"),
    NEUTRAL("Neutral", "Balanced, understated, and minimal");

    private final String label;
    private final String description;

    Mood(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
