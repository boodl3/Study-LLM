package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studyllm.chat.dto.ChatMessageResponse;
import com.studyllm.notebook.Notebook;
import com.studyllm.source.Chunk;
import com.studyllm.source.Source;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Full US1 happy path (SC-007): a seeded, READY source with a chunk that answers the question
 * yields a cited, grounded answer within 15 seconds. Requires a local Ollama server with
 * qwen3:8b and nomic-embed-text pulled (quickstart.md prerequisites) — this hits the real model,
 * it is not mocked.
 */
class ChatGroundedAnswerIT extends AbstractIntegrationTest {

  @Test
  void seededQuestionReturnsCitedAnswerWithinFifteenSeconds() throws Exception {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));

    Source source =
        sourceRepository.save(new Source(notebook.getId(), "syllabus.pdf", Source.FileType.PDF, 1024));
    source.markReady();
    sourceRepository.save(source);

    String chunkText = "The midterm project is due on March 14th and counts for 25% of the grade.";
    float[] embedding = embeddingClient.embed(chunkText);
    chunkRepository.save(new Chunk(source.getId(), chunkText, 0, "page 2", embedding));

    Instant start = Instant.now();
    String body =
        mockMvc
            .perform(
                post("/api/v1/notebooks/{id}/chat", notebook.getId())
                    .with(csrf())
                    .header("Authorization", "Bearer " + tokenFor(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\": \"What's the deadline for the midterm project?\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Duration elapsed = Duration.between(start, Instant.now());

    ChatMessageResponse response = objectMapper.readValue(body, ChatMessageResponse.class);

    assertThat(elapsed).isLessThanOrEqualTo(Duration.ofSeconds(15));
    assertThat(response.notFoundInSources()).isFalse();
    assertThat(response.citedSources()).isNotEmpty();
    assertThat(response.citedSources().get(0).filename()).isEqualTo("syllabus.pdf");
    assertThat(response.citedSources().get(0).sectionLabel()).isEqualTo("page 2");
    // Guards FR-015 (no general-knowledge leakage): the answer must actually reflect the
    // seeded chunk's content, not just carry an incidental citation.
    assertThat(response.content()).containsIgnoringCase("march 14");
  }
}
