package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class DocxTextExtractor implements TextExtractor {

  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    // DOCX carries no reliable page-boundary metadata in its raw XML, so the whole document is
    // one section — FR-016 treats a source-only citation as sufficient when a source has no
    // extractable page/section structure.
    try (XWPFDocument document = new XWPFDocument(in);
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      return List.of(new ExtractedSection(null, extractor.getText()));
    }
  }
}
