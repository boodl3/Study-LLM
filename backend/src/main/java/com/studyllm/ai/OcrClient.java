package com.studyllm.ai;

import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OCR via Tesseract, falling back to an Ollama vision model (llava by default) for images
 * Tesseract finds no text in — a diagram or photo, where a vision model at least has a chance.
 *
 * <p>Tesseract leads because it measured both far faster and far more accurate on real lecture
 * slides: 28s vs 11.5 minutes for the same 31-page scanned PDF, and it transcribes the page
 * rather than describing it. The vision model was observed inventing plausible-but-wrong content
 * (citing "Section 8.5" for a page that reads "Section 6.2"), which is the more dangerous failure
 * mode for a study tool, since nothing downstream can tell it apart from a real citation.
 */
@Component
@EnableConfigurationProperties(OllamaProperties.class)
public class OcrClient {

  private static final Logger log = LoggerFactory.getLogger(OcrClient.class);

  // General-purpose vision models default to *describing* an image ("this appears to be a book
  // cover...") rather than transcribing it, and "don't describe it" instructions alone are not
  // reliably obeyed — roughly a quarter of pages still opened with a description preamble before
  // the real content. Forcing a fixed marker before the transcription gives extractText() a
  // reliable anchor to strip the preamble at programmatically, rather than hoping the model
  // complies with the instruction on every call.
  private static final String MARKER = "===TEXT===";

  private static final String PROMPT =
      "You are an OCR engine. Respond with the exact marker \""
          + MARKER
          + "\" followed immediately by the literal text visible in the image, exactly as "
          + "written, character for character, preserving line breaks — nothing before the "
          + "marker, and nothing after the transcription but the transcription itself. Do not "
          + "describe the image. Do not summarize. Do not add commentary or explanations. If "
          + "there is no legible text, output the marker followed by nothing.";

  // Low (not zero) temperature: OCR should be near-deterministic, not creative — but temperature
  // 0 (pure greedy decoding) tested *worse* than the default on ambiguous/blurry pages, driving
  // one page into a 5-minute, 2500+ token runaway repetition that a little randomness would have
  // broken out of on its own. repeat_penalty above Ollama's 1.1 default helps the same failure
  // mode. num_predict is the actual hard guarantee: whatever temperature/repeat_penalty do,
  // generation is capped so one bad image can never run away — verified this was a real risk,
  // since the server kept decoding for minutes even after the client gave up and disconnected.
  // Measured against a real dense slide: a 512-token cap took 103s of generation alone and had
  // drifted into fabricated content ("find the power of the set X...") well before hitting it —
  // 256 caps the worst case near in half and leaves the model less room to drift off the page.
  private static final Map<String, Object> OPTIONS =
      Map.of("temperature", 0.2, "repeat_penalty", 1.3, "num_predict", 256);

  private record GenerateRequest(
      String model,
      String prompt,
      List<String> images,
      boolean stream,
      String keep_alive,
      Map<String, Object> options) {}

  private record GenerateResponse(String response, String done_reason) {}

  // A document can have dozens of OCR-able pages/images; running them one at a time serializes
  // on the full LLM generation time of each. Ollama itself may still queue these
  // (OLLAMA_NUM_PARALLEL), but overlapping the calls client-side helps whenever it doesn't.
  // It doesn't help at all on a CPU-only Ollama deployment (confirmed via /api/ps showing
  // size_vram: 0) — there's only one real worker, so concurrent calls just queue behind it.
  // studyllm.ollama.ocr-concurrency lets a CPU-only deployment set this to 1 to skip the
  // pointless thread contention; deployments with real GPU headroom can raise it instead.
  // Below this, treat the page as "Tesseract found nothing" and let the vision model try. Real
  // slides measured 84-1062 characters even when sparse, so this only catches genuinely empty
  // results rather than short-but-real ones.
  private static final int MIN_USEFUL_TEXT_LENGTH = 16;

  private final RestClient restClient;
  private final OllamaProperties properties;
  private final ExecutorService executor;

  // Tesseract instances are not thread-safe, and extractTextBatch runs several at once, so each
  // thread gets its own rather than serialising every call behind one shared instance.
  private final ThreadLocal<ITesseract> tesseract;

  // Same hang risk as EmbeddingClient, but the ceiling has to be much higher here: this path only
  // runs when Tesseract found nothing and a vision model is asked to look at the image, and a
  // cold vision model measured 168s just to load before generating a single token. The point is
  // to bound a hang, not to police slowness — 10 minutes matches OllamaChatClient's own cap.
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

  public OcrClient(
      OllamaProperties properties,
      @Value("${studyllm.ocr.tessdata-path}") String tessdataPath) {
    this.properties = properties;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    this.executor = Executors.newFixedThreadPool(properties.ocrConcurrency());
    this.tesseract =
        ThreadLocal.withInitial(
            () -> {
              Tesseract instance = new Tesseract();
              instance.setDatapath(tessdataPath);
              instance.setLanguage("eng");
              return instance;
            });
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  /**
   * Runs OCR on each image concurrently (bounded by {@code studyllm.ollama.ocr-concurrency}),
   * returning results in the same order as the input. A failed call yields "" for that image
   * rather than failing the whole batch.
   */
  public List<String> extractTextBatch(List<byte[]> images) {
    List<CompletableFuture<String>> futures =
        images.stream().map(image -> CompletableFuture.supplyAsync(() -> extractTextOrBlank(image), executor)).toList();
    return futures.stream().map(CompletableFuture::join).toList();
  }

  private String extractTextOrBlank(byte[] imageBytes) {
    try {
      return extractText(imageBytes);
    } catch (RestClientException e) {
      return "";
    }
  }

  /**
   * Runs OCR on one image, returning the transcribed text (blank if none found). Best-effort:
   * callers should treat a thrown exception as "skip this image" rather than fail ingestion,
   * since the vision model may not be installed.
   */
  public String extractText(byte[] imageBytes) {
    String text = transcribeLocally(imageBytes);
    if (text.length() >= MIN_USEFUL_TEXT_LENGTH) {
      return text;
    }
    return transcribeWithVisionModel(imageBytes);
  }

  /**
   * Tesseract pass. Returns "" rather than throwing on any failure — including a missing/broken
   * native library or tessdata directory — so a misconfigured Tesseract degrades to the vision
   * model instead of failing ingestion outright.
   *
   * <p>Deliberately passes the image at full resolution: Tesseract's accuracy depends on the
   * effective DPI of what it's given, unlike the vision model which resizes to a fixed size
   * regardless.
   */
  private String transcribeLocally(byte[] imageBytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (image == null) {
        return "";
      }
      return MathSymbolRepair.repair(tesseract.get().doOCR(image).strip());
    } catch (TesseractException | IOException | UnsatisfiedLinkError e) {
      log.warn("Tesseract OCR failed; falling back to the vision model for this image", e);
      return "";
    }
  }

  private String transcribeWithVisionModel(byte[] imageBytes) {
    String base64 = Base64.getEncoder().encodeToString(downscale(imageBytes));
    try {
      GenerateResponse response =
          restClient
              .post()
              .uri("/api/generate")
              // keep_alive: a document can have dozens of image pages OCR'd back to back, and
              // this model is slow to cold-load — keep it resident between calls in that loop.
              .body(
                  new GenerateRequest(
                      properties.visionModel(), PROMPT, List.of(base64), false, "30m", OPTIONS))
              .retrieve()
              .body(GenerateResponse.class);
      if (response != null && "length".equals(response.done_reason())) {
        log.warn(
            "OCR output hit the {}-token cap for one image; transcription is likely truncated",
            OPTIONS.get("num_predict"));
      }
      return extractAfterMarker(response == null ? null : response.response());
    } catch (RestClientException e) {
      log.warn("OCR call to Ollama ({}) failed; skipping image", properties.visionModel(), e);
      throw e;
    }
  }

  // NOT a speed optimization — measured, and it isn't one. The vision encoder resizes every
  // image to a fixed input size (both a 1500x1125 page and its 1024x768 downscale produce
  // exactly 679 prompt tokens), and an A/B over 5 pages showed prompt-eval times of 16.8s
  // full-size vs 16.6s downscaled: noise. What this does buy is a bound on payload size, so a
  // phone photo pasted into a DOCX doesn't become a multi-MB base64 string held in memory and
  // sent over HTTP for an image the model will shrink to a thumbnail anyway.
  private static final int MAX_IMAGE_DIMENSION = 1024;

  /**
   * Shrinks an image so its longest side is at most {@link #MAX_IMAGE_DIMENSION}, leaving
   * already-small images (and anything ImageIO can't decode) untouched. Best-effort: a failure
   * here returns the original bytes rather than losing the image.
   */
  static byte[] downscale(byte[] imageBytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (image == null) {
        return imageBytes;
      }
      int longestSide = Math.max(image.getWidth(), image.getHeight());
      if (longestSide <= MAX_IMAGE_DIMENSION) {
        return imageBytes;
      }
      double ratio = (double) MAX_IMAGE_DIMENSION / longestSide;
      int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
      int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));

      BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = scaled.createGraphics();
      // TYPE_INT_RGB has no alpha channel, so a transparent source (common for images pasted
      // into DOCX/PPTX) would otherwise composite onto black and hide dark text entirely.
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(image, 0, 0, width, height, null);
      graphics.dispose();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(scaled, "png", out);
      return out.toByteArray();
    } catch (IOException | IllegalArgumentException e) {
      log.warn("Failed to downscale an image before OCR; sending it as-is", e);
      return imageBytes;
    }
  }

  /**
   * Strips everything up to and including the marker, discarding any preamble the model wrote
   * before it. Falls back to the raw (trimmed) text if the model didn't include the marker at
   * all, since that's still likely to be the transcription rather than nothing. Also truncates
   * at a second marker occurrence — the model sometimes echoes it again to "close" the block
   * (e.g. `===TEXT===...content...===TEXT==="`), which would otherwise leak into the output.
   */
  static String extractAfterMarker(String raw) {
    if (raw == null) {
      return "";
    }
    int start = raw.indexOf(MARKER);
    if (start == -1) {
      return raw.trim();
    }
    start += MARKER.length();
    int end = raw.indexOf(MARKER, start);
    return (end == -1 ? raw.substring(start) : raw.substring(start, end)).trim();
  }
}
