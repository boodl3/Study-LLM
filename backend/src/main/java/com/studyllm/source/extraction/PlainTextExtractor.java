package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlainTextExtractor implements TextExtractor {

  @Override
  public List<ExtractedSection> extract(InputStream in) throws IOException {
    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    return List.of(new ExtractedSection(null, text));
  }
}
