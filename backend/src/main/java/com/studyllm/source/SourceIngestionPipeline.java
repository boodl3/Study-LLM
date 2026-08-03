package com.studyllm.source;

import com.studyllm.ai.EmbeddingClient;
import com.studyllm.source.extraction.ExtractedSection;
import com.studyllm.source.extraction.TextExtractorFactory;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload → extract → chunk → embed → persist, run off the request thread (spec SC-006: READY
 * within 2 minutes). A failure here only fails this source, never the notebook or an in-flight
 * chat (constitution Article VII) — every exception is caught and turned into
 * {@code Source.FAILED} with a human-readable reason.
 */
@Component
public class SourceIngestionPipeline {

  private static final Logger log = LoggerFactory.getLogger(SourceIngestionPipeline.class);

  private final SourceRepository sourceRepository;
  private final ChunkRepository chunkRepository;
  private final TextExtractorFactory textExtractorFactory;
  private final RecursiveChunker recursiveChunker;
  private final EmbeddingClient embeddingClient;

  public SourceIngestionPipeline(
      SourceRepository sourceRepository,
      ChunkRepository chunkRepository,
      TextExtractorFactory textExtractorFactory,
      RecursiveChunker recursiveChunker,
      EmbeddingClient embeddingClient) {
    this.sourceRepository = sourceRepository;
    this.chunkRepository = chunkRepository;
    this.textExtractorFactory = textExtractorFactory;
    this.recursiveChunker = recursiveChunker;
    this.embeddingClient = embeddingClient;
  }

  @Async
  @Transactional
  public void process(UUID sourceId, byte[] content, Source.FileType fileType) {
    MDC.put("sourceId", sourceId.toString());
    try {
      Source source = sourceRepository.findById(sourceId).orElse(null);
      if (source == null) {
        return;
      }
      try {
        List<ExtractedSection> sections =
            textExtractorFactory.forType(fileType).extract(new ByteArrayInputStream(content));
        List<ChunkDraft> drafts = recursiveChunker.chunk(sections);
        if (drafts.isEmpty()) {
          source.markFailed("No extractable text was found in this file.");
        } else {
          for (ChunkDraft draft : drafts) {
            float[] embedding = embeddingClient.embed(draft.content());
            chunkRepository.save(
                new Chunk(sourceId, draft.content(), draft.position(), draft.sectionLabel(), embedding));
          }
          source.markReady();
        }
      } catch (Exception e) {
        log.error("Source ingestion failed for source {}", sourceId, e);
        source.markFailed("Couldn't process this file: " + e.getMessage());
      }
      sourceRepository.save(source);
    } finally {
      MDC.remove("sourceId");
    }
  }
}
