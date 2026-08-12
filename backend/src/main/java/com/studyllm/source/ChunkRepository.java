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
   *
   * <p>Each hit is widened to its {@code neighborRadius} adjacent chunks in the same source,
   * because content that runs past a page break leaves the continuation almost unretrievable on
   * its own: it inherits none of the heading that makes it findable. Measured on a real lecture
   * PDF, "list all 12 set identities" ranked the page holding identities 1-6 at #2 but the page
   * holding 7-12 at #18, so the answer silently covered only half the theorem. Pulling in
   * neighbours costs prompt tokens but is what makes a split section answerable at all.
   *
   * <p>Results come back in document order rather than by distance so that a section spanning
   * several pages reads continuously in the prompt.
   */
  @Query(
      value =
          """
          WITH hits AS (
            SELECT c.source_id, c.position FROM chunks c
            JOIN sources s ON s.id = c.source_id
            WHERE s.notebook_id = :notebookId AND s.status = 'READY'
              AND (c.embedding <=> CAST(:queryEmbedding AS vector)) < :maxDistance
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
          )
          SELECT DISTINCT c.* FROM chunks c
          JOIN hits h ON h.source_id = c.source_id
          WHERE c.position BETWEEN h.position - :neighborRadius AND h.position + :neighborRadius
          ORDER BY c.source_id, c.position
          """,
      nativeQuery = true)
  List<Chunk> findNearestInReadySources(
      @Param("notebookId") UUID notebookId,
      @Param("queryEmbedding") String queryEmbedding,
      @Param("maxDistance") double maxDistance,
      @Param("limit") int limit,
      @Param("neighborRadius") int neighborRadius);

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

  /** Formats an embedding as the pgvector text literal (e.g. {@code "[0.1,0.2,0.3]"}) native queries expect. */
  static String toPgVectorLiteral(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(embedding[i]);
    }
    return sb.append(']').toString();
  }
}
