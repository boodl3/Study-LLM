package com.studyllm.source.extraction;

import com.studyllm.source.Source;
import org.springframework.stereotype.Component;

@Component
public class TextExtractorFactory {

  private final PdfTextExtractor pdfTextExtractor;
  private final DocxTextExtractor docxTextExtractor;
  private final PptxTextExtractor pptxTextExtractor;
  private final PlainTextExtractor plainTextExtractor;
  private final WebsiteTextExtractor websiteTextExtractor;

  public TextExtractorFactory(
      PdfTextExtractor pdfTextExtractor,
      DocxTextExtractor docxTextExtractor,
      PptxTextExtractor pptxTextExtractor,
      PlainTextExtractor plainTextExtractor,
      WebsiteTextExtractor websiteTextExtractor) {
    this.pdfTextExtractor = pdfTextExtractor;
    this.docxTextExtractor = docxTextExtractor;
    this.pptxTextExtractor = pptxTextExtractor;
    this.plainTextExtractor = plainTextExtractor;
    this.websiteTextExtractor = websiteTextExtractor;
  }

  /** Picks the extractor matching a source's detected file type. */
  public TextExtractor forType(Source.FileType fileType) {
    return switch (fileType) {
      case PDF -> pdfTextExtractor;
      case DOCX -> docxTextExtractor;
      case PPTX -> pptxTextExtractor;
      case TXT, MD -> plainTextExtractor;
      case URL -> websiteTextExtractor;
    };
  }
}
