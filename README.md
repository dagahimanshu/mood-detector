# 🎭 Mood Detector

A Spring Boot REST API that analyzes images to detect and classify moods/emotions. This application processes single or multiple images and returns detailed mood detection results with metrics.

## Features

- **Single Image Analysis**: Upload and analyze a single image for mood detection
- **Batch Analysis**: Analyze multiple images in a single request
- **Comprehensive Metrics**: Get detailed metrics and analysis results for each image
- **RESTful API**: Easy-to-use HTTP endpoints for integration
- **Docker Support**: Pre-configured Docker setup for containerized deployment
- **Robust Error Handling**: Global exception handling with meaningful error responses

## Tech Stack

- **Java 21** - Latest LTS Java version
- **Spring Boot 4.1.0** - Modern Spring framework
- **Lombok** - Reduce boilerplate code
- **Maven** - Dependency management and build tool
- **Docker & Docker Compose** - Container orchestration

## Prerequisites

- Java 21 or higher
- Maven 3.9+
- Docker & Docker Compose (optional, for containerized deployment)

## Project Structure

```
src/main/java/com/mooddetector/mood_detector/
├── MoodDetectorApplication.java      # Main Spring Boot application
├── controller/
│   └── ImageMoodController.java      # REST API endpoints
├── service/
│   └── ImageAnalysisService.java     # Image processing logic
├── model/
│   ├── Mood.java                     # Mood enum
│   ├── MoodResult.java               # API response model
│   ├── SingleImageResult.java        # Individual image result
│   └── ImageMetrics.java             # Image analysis metrics
└── config/
    └── GlobalExceptionHandler.java   # Global exception handling
```

## API Endpoints

### Single Image Analysis

**POST** `/api/mood/analyze`

Analyze a single image for mood detection.

**Request:**
- Content-Type: `multipart/form-data`
- Parameter: `image` (MultipartFile)

**Response:**
```json
{
  "confidence": 0.7016,
  "metrics": {
    "brightness": 0.5275,
    "contrast": 0.4677,
    "pixelCount": 2047536,
    "saturation": 0.2148,
    "warmth": 0.0846
  },
  "moodDescription": "Subdued, reflective, and wistful",
  "moodLabel": "Melancholic",
  "primaryMood": "MELANCHOLIC",
  "secondaryMoods": [
    {
      "confidence": 0.6829,
      "label": "Gloomy",
      "mood": "GLOOMY"
    },
    {
      "confidence": 0.6527,
      "label": "Warm",
      "mood": "WARM"
    }
  ]
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/mood/analyze \
  -F "image=@/path/to/image.jpg"
```

#### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `confidence` | number | Overall confidence level (0-1) of the mood detection |
| `primaryMood` | string | Primary mood detected (e.g., MELANCHOLIC, HAPPY, CALM) |
| `moodLabel` | string | Human-readable label for the primary mood |
| `moodDescription` | string | Detailed description of the mood |
| `imageWidth` | integer | Width of the analyzed image in pixels |
| `imageHeight` | integer | Height of the analyzed image in pixels |
| `metrics` | object | Detailed image analysis metrics |
| `secondaryMoods` | array | Alternative mood classifications with confidence scores |

#### Metrics

The `metrics` object contains the following analysis data:

| Metric | Description |
|--------|-------------|
| `brightness` | Brightness level of the image (0-1) |
| `contrast` | Contrast level detected (0-1) |
| `saturation` | Color saturation level (0-1) |
| `warmth` | Warmth/temperature of the image colors (0-1) |
| `pixelCount` | Total number of pixels in the image |

### Combined Image Analysis

**POST** `/api/mood/analyze/combined`

Analyze multiple images in a single request.

**Request:**
- Content-Type: `multipart/form-data`
- Parameter: `images` (List of MultipartFile)

**Response:**
```json
{
  "results": [
    {
      "confidence": 0.7016,
      "imageHeight": 1352,
      "imageWidth": 1518,
      "metrics": {
        "brightness": 0.5275,
        "contrast": 0.4677,
        "pixelCount": 2047536,
        "saturation": 0.2148,
        "warmth": 0.0846
      },
      "moodDescription": "Subdued, reflective, and wistful",
      "moodLabel": "Melancholic",
      "primaryMood": "MELANCHOLIC",
      "secondaryMoods": [
        {
          "confidence": 0.6829,
          "label": "Gloomy",
          "mood": "GLOOMY"
        }
      ]
    }
  ],
  "totalImages": 1
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/mood/analyze/combined \
  -F "images=@/path/to/image1.jpg" \
  -F "images=@/path/to/image2.jpg"
```

## Configuration

Configuration is managed through `application.properties`:

```properties
spring.application.name=mood-detector
server.port=8080

# File upload limits
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=22MB

# Logging
logging.level.com.mooddetector=DEBUG
spring.jackson.serialization.indent-output=true
```

**Key Configuration:**
- **Max File Size**: 20MB per file
- **Max Request Size**: 22MB total
- **Server Port**: 8080
- **Logging Level**: DEBUG for mood-detector package

## Getting Started

### Local Development

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd mood-detector
   ```

2. **Build the project**
   ```bash
   ./mvnw clean package
   ```

3. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Access the API**
   - The server will start on http://localhost:8080
   - Test endpoints with cURL or Postman

### Docker Deployment

1. **Build Docker image**
   ```bash
   docker build -t mood-detector:latest .
   ```

2. **Using Docker Compose**
   ```bash
   docker-compose up
   ```

3. **Access the API**
   - http://localhost:8080

## Build Information

- **Java Version**: 21
- **Build Tool**: Maven 3.9.6
- **Base Image**: eclipse-temurin:21-jre-alpine (production)
- **Multi-stage Docker Build**: Optimized for smaller image size

## Development Notes

- The application uses Lombok for reducing boilerplate code
- SLF4J is used for logging throughout the application
- Spring's dependency injection is used for service management
- Multipart file upload is enabled with configured size limits
