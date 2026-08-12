package com.studyllm.source.extraction;

import com.studyllm.ai.OcrClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PptxTextExtractor implements TextExtractor {

  private static final Logger log = LoggerFactory.getLogger(PptxTextExtractor.class);

  private final OcrClient ocrClient;

  public PptxTextExtractor(OcrClient ocrClient) {
    this.ocrClient = ocrClient;
  }

  /**
   * Extracts text shape by shape, one section per slide, skipping slides with neither text nor
   * OCR-able pictures. Picture shapes (e.g. a pasted screenshot) are OCR'd and folded into the
   * same slide section as any text shapes.
   *
   * <p>Picture bytes are gathered up front and OCR'd in one concurrent batch (rather than one
   * call per picture inline) since that's the slow part; each slide's shape list then just
   * replays literal text alongside the matching OCR result, in original shape order.
   */
  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    List<List<Object>> slideTokens = new ArrayList<>(); // each token is a String or an OCR index
    List<byte[]> ocrImages = new ArrayList<>();

    try (XMLSlideShow slideShow = new XMLSlideShow(in)) {
      for (XSLFSlide slide : slideShow.getSlides()) {
        List<Object> tokens = new ArrayList<>();
        for (XSLFShape shape : slide.getShapes()) {
          if (shape instanceof XSLFTextShape textShape) {
            tokens.add(textShape.getText() + "\n");
          } else if (shape instanceof XSLFPictureShape pictureShape) {
            try {
              tokens.add(ocrImages.size());
              ocrImages.add(pictureShape.getPictureData().getData());
            } catch (Exception e) {
              log.warn("Failed to read an embedded PPTX image; skipping", e);
            }
          }
        }
        slideTokens.add(tokens);
      }
    }

    List<String> ocrResults = ocrImages.isEmpty() ? List.of() : ocrClient.extractTextBatch(ocrImages);

    List<ExtractedSection> sections = new ArrayList<>();
    for (int i = 0; i < slideTokens.size(); i++) {
      StringBuilder text = new StringBuilder();
      for (Object token : slideTokens.get(i)) {
        if (token instanceof String literal) {
          text.append(literal);
        } else {
          String ocrText = ocrResults.get((Integer) token);
          if (!ocrText.isBlank()) {
            text.append(ocrText).append('\n');
          }
        }
      }
      if (!text.isEmpty()) {
        sections.add(new ExtractedSection("slide " + (i + 1), text.toString()));
      }
    }
    return sections;
  }
}
