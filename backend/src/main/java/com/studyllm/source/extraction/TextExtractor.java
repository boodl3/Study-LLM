package com.studyllm.source.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Pulls plain text out of one file format, split into sections the chunker can attribute (page/slide/whole file). */
public interface TextExtractor {
  List<ExtractedSection> extract(InputStream in) throws IOException;
}
