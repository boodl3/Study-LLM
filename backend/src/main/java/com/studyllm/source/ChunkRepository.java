package com.studyllm.source;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

  List<Chunk> findBySourceIdOrderByPosition(UUID sourceId);

  /**
   * Nearest-neighbor search (pgvector cosine distance, {@code <=>}) over the READY sources of one
   * notebook, dropping anything past {@code maxDistance} (spec FR-017 — never guess when nothing
   * is actually relevant). {@code queryEmbedding} is a pgvector text literal, e.g. {@code
   * "[0.1,0.2,0.3]"} — see {@link #toPgVectorLiteral(float[])}.
   */
  @Query(
      value =
          """
          SELECT c.* FROM chunks c
          JOIN sources s ON s.id = c.source_id
          WHERE s.notebook_id = :notebookId AND s.status = 'READY'
            AND (c.embedding <=> CAST(:queryEmbedding AS vector)) < :maxDistance
          ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
          LIMIT :limit
          """,
      nativeQuery = true)
  List<Chunk> findNearestInReadySources(
      @Param("notebookId") UUID notebookId,
      @Param("queryEmbedding") String queryEmbedding,
      @Param("maxDistance") double maxDistance,
      @Param("limit") int limit);

  /** Whether any chunk belonging to the given notebook's sources contains {@code term} (US4
   * content search, FR-008), scoped by the caller in {@code NotebookSearchService}. */
  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1 FROM chunks c
            JOIN sources s ON s.id = c.source_id
            WHERE s.notebook_id = :notebookId AND c.content ILIKE CONCAT('%', :term, '%')
          )
          """,
      nativeQuery = true)
  boolean existsContentMatch(@Param("notebookId") UUID notebookId, @Param("term") String term);

  static String toPgVectorLiteral(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(embedding[i]);
    }
    return sb.append(']').toString();
  }
}
