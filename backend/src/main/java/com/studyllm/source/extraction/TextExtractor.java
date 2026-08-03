package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface TextExtractor {
  List<ExtractedSection> extract(InputStream in) throws IOException;
}
