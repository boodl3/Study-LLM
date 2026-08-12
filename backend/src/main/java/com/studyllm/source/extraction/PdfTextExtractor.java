package com.studyllm.source.extraction;

import com.studyllm.ai.OcrClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor implements TextExtractor {

  private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

  private final OcrClient ocrClient;

  public PdfTextExtractor(OcrClient ocrClient) {
    this.ocrClient = ocrClient;
  }

  /**
   * Extracts text page by page so each chunk can later cite a specific page number. A page with
   * no extractable text (a scanned page, or a screenshot pasted in as a full-page image) is
   * rendered to an image; all such images are then OCR'd concurrently, since rendering (via a
   * single shared PDFRenderer) isn't safe to parallelize but the OCR network calls are.
   */
  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    Map<Integer, String> textByPage = new TreeMap<>();
    List<Integer> ocrPageNumbers = new ArrayList<>();
    List<byte[]> ocrImages = new ArrayList<>();

    try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
      PDFTextStripper stripper = new PDFTextStripper();
      PDFRenderer renderer = new PDFRenderer(document);
      int pageCount = document.getNumberOfPages();
      for (int page = 1; page <= pageCount; page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = stripper.getText(document);
        if (text != null && !text.isBlank()) {
          textByPage.put(page, text);
        } else {
          int pageNumber = page;
          renderPage(renderer, page - 1)
              .ifPresent(
                  image -> {
                    ocrPageNumbers.add(pageNumber);
                    ocrImages.add(image);
                  });
        }
      }
    }

    List<String> ocrResults = ocrImages.isEmpty() ? List.of() : ocrClient.extractTextBatch(ocrImages);
    for (int i = 0; i < ocrPageNumbers.size(); i++) {
      String text = ocrResults.get(i);
      if (text != null && !text.isBlank()) {
        textByPage.put(ocrPageNumbers.get(i), text);
      }
    }

    List<ExtractedSection> sections = new ArrayList<>();
    textByPage.forEach((page, text) -> sections.add(new ExtractedSection("page " + page, text)));
    return sections;
  }

  private Optional<byte[]> renderPage(PDFRenderer renderer, int pageIndex) {
    try {
      BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return Optional.of(out.toByteArray());
    } catch (Exception e) {
      log.warn("Failed to render PDF page {} for OCR; skipping", pageIndex + 1, e);
      return Optional.empty();
    }
  }
}
