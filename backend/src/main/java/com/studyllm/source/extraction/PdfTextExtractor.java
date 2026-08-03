package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor implements TextExtractor {

  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    List<ExtractedSection> sections = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
      PDFTextStripper stripper = new PDFTextStripper();
      int pageCount = document.getNumberOfPages();
      for (int page = 1; page <= pageCount; page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = stripper.getText(document);
        if (text != null && !text.isBlank()) {
          sections.add(new ExtractedSection("page " + page, text));
        }
      }
    }
    return sections;
  }
}
