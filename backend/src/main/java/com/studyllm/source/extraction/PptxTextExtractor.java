package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

@Component
public class PptxTextExtractor implements TextExtractor {

  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    List<ExtractedSection> sections = new ArrayList<>();
    try (XMLSlideShow slideShow = new XMLSlideShow(in)) {
      List<XSLFSlide> slides = slideShow.getSlides();
      for (int i = 0; i < slides.size(); i++) {
        StringBuilder text = new StringBuilder();
        for (XSLFShape shape : slides.get(i).getShapes()) {
          if (shape instanceof XSLFTextShape textShape) {
            text.append(textShape.getText()).append('\n');
          }
        }
        if (!text.isEmpty()) {
          sections.add(new ExtractedSection("slide " + (i + 1), text.toString()));
        }
      }
    }
    return sections;
  }
}
