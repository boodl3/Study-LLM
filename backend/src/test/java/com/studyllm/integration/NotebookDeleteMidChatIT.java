package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.studyllm.notebook.Notebook;
import com.studyllm.source.Chunk;
import com.studyllm.source.Source;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A notebook deleted while a chat request is in flight must not let that request succeed
 * (FR-021). The chat call's real Ollama generation step (multi-second) gives the concurrent
 * delete a wide, realistic window to land before the final {@code ChatMessage} insert.
 *
 * <p>The chat endpoint streams Server-Sent Events, so the HTTP status commits to 200 as soon as
 * the first answer token arrives — well before the final persistence step this test races
 * against. Failure is therefore observed as an {@code error} event in the SSE body, not a
 * non-200 status; see {@code ChatController#askQuestion}.
 */
class NotebookDeleteMidChatIT extends AbstractIntegrationTest {

  @Test
  void deletingNotebookMidRequestFailsTheInFlightChatGracefully() throws Exception {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));
    String auth = "Bearer " + tokenFor(userId);

    Source source =
        sourceRepository.save(new Source(notebook.getId(), "syllabus.pdf", Source.FileType.PDF, 1024));
    source.markReady();
    sourceRepository.save(source);
    String chunkText = "The midterm project is due on March 14th and counts for 25% of the grade.";
    chunkRepository.save(
        new Chunk(source.getId(), chunkText, 0, "page 2", embeddingClient.embed(chunkText)));

    CompletableFuture<MvcResult> chatCall =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return mockMvc
                    .perform(
                        post("/api/v1/notebooks/{id}/chat", notebook.getId())
                            .with(csrf())
                            .header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"What's the deadline for the midterm project?\"}"))
                    .andReturn();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    // Give the request time to pass the ownership/zero-source checks and start the (slow) LLM
    // call, then delete the notebook out from under it.
    TimeUnit.MILLISECONDS.sleep(500);
    mockMvc
        .perform(delete("/api/v1/notebooks/{id}", notebook.getId()).with(csrf()).header("Authorization", auth))
        .andReturn();

    MvcResult chatResult = chatCall.get(30, TimeUnit.SECONDS);
    String body = chatResult.getResponse().getContentAsString();

    assertThat(body).contains("\"error\":true");
    assertThat(notebookRepository.findByIdAndOwnerId(notebook.getId(), userId)).isEmpty();
  }
}
