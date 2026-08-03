package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studyllm.notebook.dto.NotebookDto;
import com.studyllm.notebook.dto.NotebookListResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** create -> rename -> delete, asserting the Home list reflects each change immediately. */
class NotebookLifecycleIT extends AbstractIntegrationTest {

  @Test
  void createRenameDeleteReflectsImmediatelyInList() throws Exception {
    UUID userId = createUserAndGetId();
    String auth = "Bearer " + tokenFor(userId);

    String createBody =
        mockMvc
            .perform(
                post("/api/v1/notebooks")
                    .with(csrf())
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\": \"Bio 101\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    NotebookDto created = objectMapper.readValue(createBody, NotebookDto.class);

    NotebookListResponse afterCreate = listNotebooks(auth);
    assertThat(afterCreate.notebooks()).hasSize(1);
    assertThat(afterCreate.notebooks().get(0).title()).isEqualTo("Bio 101");

    mockMvc
        .perform(
            patch("/api/v1/notebooks/{id}", created.id())
                .with(csrf())
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Biology 101\"}"))
        .andExpect(status().isOk());

    NotebookListResponse afterRename = listNotebooks(auth);
    assertThat(afterRename.notebooks()).hasSize(1);
    assertThat(afterRename.notebooks().get(0).title()).isEqualTo("Biology 101");

    mockMvc
        .perform(delete("/api/v1/notebooks/{id}", created.id()).with(csrf()).header("Authorization", auth))
        .andExpect(status().isNoContent());

    NotebookListResponse afterDelete = listNotebooks(auth);
    assertThat(afterDelete.notebooks()).isEmpty();
  }

  private NotebookListResponse listNotebooks(String auth) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/notebooks").header("Authorization", auth))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, NotebookListResponse.class);
  }
}
