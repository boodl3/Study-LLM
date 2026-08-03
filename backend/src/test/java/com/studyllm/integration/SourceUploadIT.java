package com.studyllm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studyllm.chat.dto.ChatMessageResponse;
import com.studyllm.notebook.Notebook;
import com.studyllm.source.Source;
import com.studyllm.source.dto.SourceDto;
import com.studyllm.source.dto.UploadResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Upload a real PDF, poll until READY within the SC-006 2-minute budget, then confirm US1's
 * chat can answer a question from it — the full core loop end to end.
 */
class SourceUploadIT extends AbstractIntegrationTest {

  @Test
  void uploadedPdfBecomesReadyThenAnswersChatQuestion() throws Exception {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));
    String auth = "Bearer " + tokenFor(userId);

    byte[] pdfBytes = pdfWithText("The midterm project is due on March 14th and counts for 25% of the grade.");
    MockMultipartFile file = new MockMultipartFile("file", "syllabus.pdf", "application/pdf", pdfBytes);

    String uploadBody =
        mockMvc
            .perform(
                multipart("/api/v1/notebooks/{id}/sources", notebook.getId())
                    .file(file)
                    .with(csrf())
                    .header("Authorization", auth))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UploadResponse upload = objectMapper.readValue(uploadBody, UploadResponse.class);

    SourceDto polled = pollUntilProcessed(notebook.getId(), upload.id(), auth);
    assertThat(polled.status()).isEqualTo(Source.ProcessingStatus.READY);

    String chatBody =
        mockMvc
            .perform(
                post("/api/v1/notebooks/{id}/chat", notebook.getId())
                    .with(csrf())
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\": \"When is the midterm project due?\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ChatMessageResponse chatResponse = objectMapper.readValue(chatBody, ChatMessageResponse.class);

    assertThat(chatResponse.notFoundInSources()).isFalse();
    assertThat(chatResponse.citedSources()).isNotEmpty();
    assertThat(chatResponse.citedSources().get(0).filename()).isEqualTo("syllabus.pdf");
  }

  @Test
  void failedUploadDoesNotAffectOtherSourcesOrAnInFlightChat() throws Exception {
    UUID userId = createUserAndGetId();
    Notebook notebook = notebookRepository.save(new Notebook(userId, "Bio 101"));
    String auth = "Bearer " + tokenFor(userId);

    byte[] goodPdf = pdfWithText("The midterm project is due on March 14th.");
    MockMultipartFile goodFile = new MockMultipartFile("file", "syllabus.pdf", "application/pdf", goodPdf);
    String goodUploadBody =
        mockMvc
            .perform(
                multipart("/api/v1/notebooks/{id}/sources", notebook.getId())
                    .file(goodFile)
                    .with(csrf())
                    .header("Authorization", auth))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID goodSourceId = objectMapper.readValue(goodUploadBody, UploadResponse.class).id();
    SourceDto goodSource = pollUntilProcessed(notebook.getId(), goodSourceId, auth);
    assertThat(goodSource.status()).isEqualTo(Source.ProcessingStatus.READY);

    // A PDF with no extractable text (blank page, no content stream) fails ingestion.
    byte[] emptyPdf = pdfWithText("");
    MockMultipartFile badFile = new MockMultipartFile("file", "blank.pdf", "application/pdf", emptyPdf);
    String badUploadBody =
        mockMvc
            .perform(
                multipart("/api/v1/notebooks/{id}/sources", notebook.getId())
                    .file(badFile)
                    .with(csrf())
                    .header("Authorization", auth))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID badSourceId = objectMapper.readValue(badUploadBody, UploadResponse.class).id();
    SourceDto badSource = pollUntilProcessed(notebook.getId(), badSourceId, auth);
    assertThat(badSource.status()).isEqualTo(Source.ProcessingStatus.FAILED);
    assertThat(badSource.failureReason()).isNotBlank();

    // The good source is untouched by the other source's failure.
    SourceDto stillGood = pollUntilProcessed(notebook.getId(), goodSourceId, auth);
    assertThat(stillGood.status()).isEqualTo(Source.ProcessingStatus.READY);

    // Chat, which only reads READY sources, still answers from the good one.
    String chatBody =
        mockMvc
            .perform(
                post("/api/v1/notebooks/{id}/chat", notebook.getId())
                    .with(csrf())
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\": \"When is the midterm project due?\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ChatMessageResponse chatResponse = objectMapper.readValue(chatBody, ChatMessageResponse.class);
    assertThat(chatResponse.notFoundInSources()).isFalse();
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
        if (!text.isEmpty()) {
          stream.beginText();
          stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          stream.newLineAtOffset(50, 700);
          stream.showText(text);
          stream.endText();
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
