package com.studyllm.source.extraction;

import com.studyllm.ai.OcrClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.stereotype.Component;

@Component
public class DocxTextExtractor implements TextExtractor {

  private final OcrClient ocrClient;

  public DocxTextExtractor(OcrClient ocrClient) {
    this.ocrClient = ocrClient;
  }

  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    // DOCX carries no reliable page-boundary metadata in its raw XML, so the whole document is
    // one section — FR-016 treats a source-only citation as sufficient when a source has no
    // extractable page/section structure.
    try (XWPFDocument document = new XWPFDocument(in);
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      List<ExtractedSection> sections = new ArrayList<>();
      sections.add(new ExtractedSection(null, extractor.getText()));

      List<byte[]> images = document.getAllPictures().stream().map(XWPFPictureData::getData).toList();
      List<String> ocrResults = images.isEmpty() ? List.of() : ocrClient.extractTextBatch(images);
      for (String text : ocrResults) {
        if (!text.isBlank()) {
          sections.add(new ExtractedSection("image", text));
        }
      }
      return sections;
    }
  }
}
