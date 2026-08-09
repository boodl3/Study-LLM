package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studyllm.chat.dto.ChatMessageResponse;
import com.studyllm.notebook.Notebook;
import com.studyllm.source.Chunk;
import com.studyllm.source.Source;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** A question unrelated to any chunk must never be answered from general knowledge (FR-017). */
class ChatNotFoundIT extends AbstractIntegrationTest {

  @Test
  void unrelatedQuestionReturnsNotFoundWithNoCitation() throws Exception {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));

    Source source =
        sourceRepository.save(new Source(notebook.getId(), "syllabus.pdf", Source.FileType.PDF, 1024));
    source.markReady();
    sourceRepository.save(source);

    String chunkText = "The midterm project is due on March 14th and counts for 25% of the grade.";
    float[] embedding = embeddingClient.embed(chunkText);
    chunkRepository.save(new Chunk(source.getId(), chunkText, 0, "page 2", embedding));

    String body =
        mockMvc
            .perform(
                post("/api/v1/notebooks/{id}/chat", notebook.getId())
                    .with(csrf())
                    .header("Authorization", "Bearer " + tokenFor(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\": \"What is the capital of France?\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ChatMessageResponse response = parseFinalChatMessage(body);

    assertThat(response.notFoundInSources()).isTrue();
    assertThat(response.citedSources()).isEmpty();
  }
}
