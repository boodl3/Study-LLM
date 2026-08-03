package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studyllm.notebook.Notebook;
import com.studyllm.notebook.dto.NotebookListResponse;
import com.studyllm.source.Chunk;
import com.studyllm.source.Source;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A title-only term and a source-content-only term must each return the correct notebook. */
class NotebookSearchIT extends AbstractIntegrationTest {

  @Test
  void searchMatchesByTitleAndBySourceContent() throws Exception {
    UUID userId = createUserAndGetId();
    String auth = "Bearer " + tokenFor(userId);

    Notebook chemistryNotebook = notebookRepository.save(new Notebook(userId, "Chemistry Notes"));
    Notebook biologyNotebook = notebookRepository.save(new Notebook(userId, "Biology 101"));

    Source biologySource =
        sourceRepository.save(
            new Source(biologyNotebook.getId(), "cells.pdf", Source.FileType.PDF, 1024));
    biologySource.markReady();
    sourceRepository.save(biologySource);
    String chunkText = "Mitochondria are the powerhouse of the cell.";
    chunkRepository.save(
        new Chunk(biologySource.getId(), chunkText, 0, "page 1", embeddingClient.embed(chunkText)));

    NotebookListResponse byTitle = search(auth, "Chemistry");
    assertThat(byTitle.notebooks()).extracting("id").containsExactly(chemistryNotebook.getId());

    NotebookListResponse byContent = search(auth, "mitochondria");
    assertThat(byContent.notebooks()).extracting("id").containsExactly(biologyNotebook.getId());

    NotebookListResponse noMatch = search(auth, "quantum entanglement nonsense");
    assertThat(noMatch.notebooks()).isEmpty();
  }

  private NotebookListResponse search(String auth, String query) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/notebooks/search").param("q", query).header("Authorization", auth))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, NotebookListResponse.class);
  }
}
