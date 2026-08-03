package com.studyllm.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyllm.integration.AbstractIntegrationTest;
import com.studyllm.notebook.Notebook;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ChunkRepository#findNearestInReadySources} against a real pgvector-enabled
 * Postgres (constitution Article V — this native query has no coverage otherwise). Uses
 * synthetic unit vectors instead of real embeddings so distances are exact and deterministic.
 */
class ChunkRepositoryIT extends AbstractIntegrationTest {

  private static final int DIMENSIONS = 768;

  @Test
  void ordersByCosineDistanceAndExcludesNonReadySourcesAndFarChunks() {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));

    Source readySource =
        sourceRepository.save(new Source(notebook.getId(), "ready.pdf", Source.FileType.PDF, 100));
    readySource.markReady();
    sourceRepository.save(readySource);

    Source processingSource =
        sourceRepository.save(new Source(notebook.getId(), "processing.pdf", Source.FileType.PDF, 100));
    // left PROCESSING deliberately — must never show up in retrieval regardless of distance.

    float[] queryVector = unitVector(0);
    Chunk exactMatch = chunkRepository.save(new Chunk(readySource.getId(), "closest", 0, null, unitVector(0)));
    Chunk orthogonal = chunkRepository.save(new Chunk(readySource.getId(), "far", 1, null, unitVector(1)));
    chunkRepository.save(new Chunk(processingSource.getId(), "should be excluded", 0, null, unitVector(0)));

    List<Chunk> results =
        chunkRepository.findNearestInReadySources(
            notebook.getId(), ChunkRepository.toPgVectorLiteral(queryVector), 0.5, 10);

    assertThat(results).extracting(Chunk::getId).containsExactly(exactMatch.getId());
    assertThat(results).extracting(Chunk::getId).doesNotContain(orthogonal.getId());
  }

  @Test
  void scopesResultsToTheGivenNotebook() {
    UUID userId = createUserAndGetId();
    Notebook notebookA = notebookRepository.save(new Notebook(userId, "Notebook A"));
    Notebook notebookB = notebookRepository.save(new Notebook(userId, "Notebook B"));

    Source sourceA = sourceRepository.save(new Source(notebookA.getId(), "a.pdf", Source.FileType.PDF, 100));
    sourceA.markReady();
    sourceRepository.save(sourceA);
    Source sourceB = sourceRepository.save(new Source(notebookB.getId(), "b.pdf", Source.FileType.PDF, 100));
    sourceB.markReady();
    sourceRepository.save(sourceB);

    chunkRepository.save(new Chunk(sourceA.getId(), "in A", 0, null, unitVector(0)));
    chunkRepository.save(new Chunk(sourceB.getId(), "in B", 0, null, unitVector(0)));

    List<Chunk> resultsForA =
        chunkRepository.findNearestInReadySources(
            notebookA.getId(), ChunkRepository.toPgVectorLiteral(unitVector(0)), 0.5, 10);

    assertThat(resultsForA).hasSize(1);
    assertThat(resultsForA.get(0).getSourceId()).isEqualTo(sourceA.getId());
  }

  private static float[] unitVector(int axis) {
    float[] v = new float[DIMENSIONS];
    v[axis] = 1.0f;
    return v;
  }
}
