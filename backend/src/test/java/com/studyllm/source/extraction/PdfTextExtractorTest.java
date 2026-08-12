package com.studyllm.source.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studyllm.ai.OcrClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdfTextExtractorTest {

  @Mock private OcrClient ocrClient;

  private PdfTextExtractor extractor;

  @BeforeEach
  void setUp() {
    extractor = new PdfTextExtractor(ocrClient);
  }

  @Test
  void skipsOcrForPagesWithExtractableText() throws IOException {
    List<ExtractedSection> sections = extractor.extract(new ByteArrayInputStream(pdf("hello")));

    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).text()).contains("hello");
    verify(ocrClient, never()).extractTextBatch(any());
  }

  @Test
  void fallsBackToOcrForATextlessPage() throws IOException {
    when(ocrClient.extractTextBatch(any())).thenReturn(List.of("scanned text via OCR"));

    List<ExtractedSection> sections = extractor.extract(new ByteArrayInputStream(pdf("")));

    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).label()).isEqualTo("page 1");
    assertThat(sections.get(0).text()).isEqualTo("scanned text via OCR");
  }

  @Test
  void skipsAPageWhenOcrFindsNoText() throws IOException {
    when(ocrClient.extractTextBatch(any())).thenReturn(List.of(""));

    assertThat(extractor.extract(new ByteArrayInputStream(pdf("")))).isEmpty();
  }

  private byte[] pdf(String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        if (!text.isEmpty()) {
          stream.beginText();
          stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          stream.newLineAtOffset(50, 700);
          stream.showText(text);
          stream.endText();
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
