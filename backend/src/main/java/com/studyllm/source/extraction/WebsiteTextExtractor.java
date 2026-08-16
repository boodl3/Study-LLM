package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** Strips boilerplate (nav/script/style/ads) from a fetched web page and extracts its readable text. */
@Component
public class WebsiteTextExtractor implements TextExtractor {

  private static final String CONTENT_TAGS = "h1, h2, h3, h4, h5, h6, p, li, blockquote, pre, td, th";
  private static final String BOILERPLATE_TAGS =
      "script, style, noscript, nav, header, footer, aside, form, iframe, svg, button";

  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    Document doc = Jsoup.parse(in, null, "");
    doc.select(BOILERPLATE_TAGS).remove();

    StringBuilder text = new StringBuilder();
    for (Element el : doc.body().select(CONTENT_TAGS)) {
      String elementText = el.text().trim();
      if (!elementText.isEmpty()) {
        text.append(elementText).append("\n\n");
      }
    }
    // Some pages don't use semantic content tags at all; fall back to whatever body text remains.
    String content = text.isEmpty() ? doc.body().text() : text.toString().trim();

    return List.of(new ExtractedSection(null, content));
  }
}
