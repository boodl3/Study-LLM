package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studyllm.notebook.Notebook;
import com.studyllm.source.Source;
import com.studyllm.source.dto.SourceDto;
import com.studyllm.source.dto.UploadResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Two concurrent uploads to the same notebook must each reach READY/FAILED independently,
 * with no corrupted or overwritten status (spec Edge Cases — concurrent uploads). */
class ConcurrentUploadIT extends AbstractIntegrationTest {

  @Test
  void twoConcurrentUploadsAreTrackedIndependently() throws Exception {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));
    String auth = "Bearer " + tokenFor(userId);

    MockMultipartFile fileA =
        new MockMultipartFile("file", "lecture-a.pdf", "application/pdf", pdfWithText("Lecture A content."));
    MockMultipartFile fileB =
        new MockMultipartFile("file", "lecture-b.pdf", "application/pdf", pdfWithText("Lecture B content."));

    CompletableFuture<UploadResponse> uploadA =
        CompletableFuture.supplyAsync(() -> upload(notebook.getId(), fileA, auth));
    CompletableFuture<UploadResponse> uploadB =
        CompletableFuture.supplyAsync(() -> upload(notebook.getId(), fileB, auth));
    CompletableFuture.allOf(uploadA, uploadB).get();

    SourceDto resultA = pollUntilProcessed(notebook.getId(), uploadA.get().id(), auth);
    SourceDto resultB = pollUntilProcessed(notebook.getId(), uploadB.get().id(), auth);

    assertThat(resultA.status()).isEqualTo(Source.ProcessingStatus.READY);
    assertThat(resultB.status()).isEqualTo(Source.ProcessingStatus.READY);
    assertThat(resultA.filename()).isEqualTo("lecture-a.pdf");
    assertThat(resultB.filename()).isEqualTo("lecture-b.pdf");
    assertThat(resultA.id()).isNotEqualTo(resultB.id());

    List<SourceDto> all =
        objectMapper
            .readValue(
                mockMvc
                    .perform(get("/api/v1/notebooks/{id}/sources", notebook.getId()).header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                com.studyllm.source.dto.SourceListResponse.class)
            .sources();
    assertThat(all).hasSize(2);
    assertThat(all).allSatisfy(s -> assertThat(s.status()).isEqualTo(Source.ProcessingStatus.READY));
  }

  private UploadResponse upload(UUID notebookId, MockMultipartFile file, String auth) {
    try {
      String body =
          mockMvc
              .perform(
                  multipart("/api/v1/notebooks/{id}/sources", notebookId)
                      .file(file)
                      .with(csrf())
                      .header("Authorization", auth))
              .andExpect(status().isAccepted())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return objectMapper.readValue(body, UploadResponse.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private SourceDto pollUntilProcessed(UUID notebookId, UUID sourceId, String auth) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
    SourceDto latest;
    do {
      String body =
          mockMvc
              .perform(
                  get("/api/v1/notebooks/{notebookId}/sources/{sourceId}", notebookId, sourceId)
                      .header("Authorization", auth))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      latest = objectMapper.readValue(body, SourceDto.class);
      if (latest.status() != Source.ProcessingStatus.PROCESSING) {
        return latest;
      }
      Thread.sleep(1000);
    } while (Instant.now().isBefore(deadline));
    return latest;
  }

  private byte[] pdfWithText(String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        stream.showText(text);
        stream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
