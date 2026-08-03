package com.studyllm.source;

import com.studyllm.source.extraction.ExtractedSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Structure-aware recursive chunking (constitution Article IV, research.md §5). Splits in
 * priority order: section/page boundary (given by the extractor) → paragraph → sentence → hard
 * character limit, then greedily packs the resulting units into ~300–500 token chunks (approximated
 * as 1,200–2,000 characters) with ~12.5% overlap between adjacent chunks.
 */
@Component
public class RecursiveChunker {

  private static final int TARGET_MIN_CHARS = 1200;
  private static final int TARGET_MAX_CHARS = 2000;
  private static final double OVERLAP_RATIO = 0.125;

  private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n");
  private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

  public List<ChunkDraft> chunk(List<ExtractedSection> sections) {
    List<ChunkDraft> chunks = new ArrayList<>();
    int position = 0;
    for (ExtractedSection section : sections) {
      List<String> units = toUnits(section.text());
      StringBuilder buffer = new StringBuilder();
      for (String unit : units) {
        if (!buffer.isEmpty() && buffer.length() + 1 + unit.length() > TARGET_MAX_CHARS) {
          chunks.add(new ChunkDraft(buffer.toString(), position++, section.label()));
          // Bound the overlap seed by the room left after this unit, so a near-max-size unit
          // (e.g. a hard-split fragment) can never push the new buffer past TARGET_MAX_CHARS.
          int overlapBudget = TARGET_MAX_CHARS - 1 - unit.length();
          buffer = new StringBuilder(overlapTail(buffer.toString(), overlapBudget));
        }
        if (!buffer.isEmpty()) {
          buffer.append('\n');
        }
        buffer.append(unit);
      }
      if (!buffer.isEmpty()) {
        chunks.add(new ChunkDraft(buffer.toString(), position++, section.label()));
      }
    }
    return chunks;
  }

  /** Flattens a section's text into units no larger than {@code TARGET_MAX_CHARS}, splitting
   * paragraph → sentence → hard limit only as far as each piece actually requires. */
  private List<String> toUnits(String text) {
    List<String> units = new ArrayList<>();
    for (String paragraph : PARAGRAPH_SPLIT.split(text.strip())) {
      String trimmed = paragraph.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.length() <= TARGET_MAX_CHARS) {
        units.add(trimmed);
        continue;
      }
      for (String sentence : SENTENCE_SPLIT.split(trimmed)) {
        String s = sentence.strip();
        if (s.isEmpty()) {
          continue;
        }
        if (s.length() <= TARGET_MAX_CHARS) {
          units.add(s);
        } else {
          for (int i = 0; i < s.length(); i += TARGET_MAX_CHARS) {
            units.add(s.substring(i, Math.min(i + TARGET_MAX_CHARS, s.length())));
          }
        }
      }
    }
    return units;
  }

  private String overlapTail(String flushedChunk, int maxLen) {
    int overlapLen = Math.min((int) Math.round(flushedChunk.length() * OVERLAP_RATIO), maxLen);
    if (overlapLen <= 0 || overlapLen >= flushedChunk.length()) {
      return "";
    }
    int start = flushedChunk.length() - overlapLen;
    int spaceIdx = flushedChunk.indexOf(' ', start);
    return spaceIdx == -1 ? flushedChunk.substring(start) : flushedChunk.substring(spaceIdx + 1);
  }

  static int targetMinChars() {
    return TARGET_MIN_CHARS;
  }

  static int targetMaxChars() {
    return TARGET_MAX_CHARS;
  }
}
