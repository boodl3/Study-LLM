package com.studyllm.source;

import com.studyllm.ai.EmbeddingClient;
import com.studyllm.source.extraction.ExtractedSection;
import com.studyllm.source.extraction.TextExtractorFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
  private final TransactionTemplate transactionTemplate;

  public SourceIngestionPipeline(
      SourceRepository sourceRepository,
      ChunkRepository chunkRepository,
      TextExtractorFactory textExtractorFactory,
      RecursiveChunker recursiveChunker,
      EmbeddingClient embeddingClient,
      TransactionTemplate transactionTemplate) {
    this.sourceRepository = sourceRepository;
    this.chunkRepository = chunkRepository;
    this.textExtractorFactory = textExtractorFactory;
    this.recursiveChunker = recursiveChunker;
    this.embeddingClient = embeddingClient;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Runs the full pipeline for one uploaded file: extract text, chunk it, embed each chunk, and
   * persist the chunks — flipping the source to READY on success or FAILED (with a reason) on
   * any error. Runs on a separate thread ({@code @Async}) so upload requests return immediately.
   *
   * <p>Deliberately not {@code @Transactional} as a whole: extraction can take many minutes when
   * a scanned document falls back to OCR (measured at 11+ minutes for a 31-page scanned PDF), and
   * wrapping that would pin a connection from the (default size 10) pool for the entire run — a
   * handful of concurrent scanned uploads would starve the pool and stall unrelated requests.
   * Only the writes are transactional, so chunks and the source's terminal status still land
   * atomically.
   */
  @Async
  public void process(UUID sourceId, byte[] content, Source.FileType fileType) {
    MDC.put("sourceId", sourceId.toString());
    try {
      Source source = sourceRepository.findById(sourceId).orElse(null);
      if (source == null) {
        return;
      }
      List<Chunk> chunks = new ArrayList<>();
      try {
        List<ExtractedSection> sections =
            textExtractorFactory.forType(fileType).extract(new ByteArrayInputStream(content));
        List<ChunkDraft> drafts = recursiveChunker.chunk(sections);
        if (drafts.isEmpty()) {
          source.markFailed("No extractable text was found in this file.");
        } else {
          for (ChunkDraft draft : drafts) {
            float[] embedding = embeddingClient.embed(draft.content());
            chunks.add(
                new Chunk(sourceId, draft.content(), draft.position(), draft.sectionLabel(), embedding));
          }
          source.markReady();
        }
      } catch (Exception e) {
        log.error("Source ingestion failed for source {}", sourceId, e);
        source.markFailed("Couldn't process this file: " + e.getMessage());
        chunks.clear();
      }
      transactionTemplate.executeWithoutResult(
          status -> {
            chunkRepository.saveAll(chunks);
            sourceRepository.save(source);
          });
    } finally {
      MDC.remove("sourceId");
    }
  }
}
