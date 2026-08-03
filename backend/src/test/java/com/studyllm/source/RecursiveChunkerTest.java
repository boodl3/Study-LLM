package com.studyllm.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyllm.source.extraction.ExtractedSection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecursiveChunkerTest {

  private final RecursiveChunker chunker = new RecursiveChunker();

  @Test
  void packsShortParagraphsIntoASingleChunk() {
    String text = "First short paragraph.\n\nSecond short paragraph.\n\nThird short paragraph.";
    List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedSection("page 1", text)));

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).content()).contains("First short paragraph.", "Third short paragraph.");
  }

  @Test
  void propagatesSectionLabelToEveryChunk() {
    String text = "Some content in this section.";
    List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedSection("page 3", text)));

    assertThat(chunks).allSatisfy(c -> assertThat(c.sectionLabel()).isEqualTo("page 3"));
  }

  @Test
  void splitsLongTextIntoTargetSizedChunksWithOverlap() {
    // 200 sentences of ~40 chars each -> ~8000 chars total, forcing multiple chunks.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 200; i++) {
      sb.append("This is generated sentence number ").append(i).append(". ");
    }
    List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedSection(null, sb.toString())));

    assertThat(chunks.size()).isGreaterThan(1);
    for (ChunkDraft chunk : chunks) {
      assertThat(chunk.content().length()).isLessThanOrEqualTo(2000);
    }
    // Positions are assigned in increasing order.
    for (int i = 0; i < chunks.size(); i++) {
      assertThat(chunks.get(i).position()).isEqualTo(i);
    }

    // Adjacent chunks overlap: the tail of chunk[i] reappears at the head of chunk[i+1].
    String firstChunk = chunks.get(0).content();
    String secondChunk = chunks.get(1).content();
    String tailOfFirst = firstChunk.substring(Math.max(0, firstChunk.length() - 50));
    String firstWordsOfTail = tailOfFirst.split("\\s+")[tailOfFirst.split("\\s+").length - 1];
    assertThat(secondChunk).contains(firstWordsOfTail);
  }

  @Test
  void hardSplitsASingleWordLongerThanTheChunkLimit() {
    String unsplittable = "a".repeat(5000);
    List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedSection(null, unsplittable)));

    assertThat(chunks.size()).isGreaterThan(1);
    for (ChunkDraft chunk : chunks) {
      assertThat(chunk.content().length()).isLessThanOrEqualTo(2000);
    }
  }
}
