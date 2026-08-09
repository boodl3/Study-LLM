package com.studyllm.chat;

import com.studyllm.chat.dto.AskQuestionRequest;
import com.studyllm.chat.dto.ChatHistoryResponse;
import com.studyllm.chat.dto.ChatMessageResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** REST endpoints for a notebook's chat: reading history and asking a grounded question. */
@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/chat")
public class ChatController {

  private final ChatService chatService;
  private final ObjectMapper objectMapper;

  public ChatController(ChatService chatService, ObjectMapper objectMapper) {
    this.chatService = chatService;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public ChatHistoryResponse getHistory(@PathVariable UUID notebookId) {
    return chatService.getHistory(notebookId);
  }

  /**
   * Streams the answer as Server-Sent Events so the first tokens render immediately instead of
   * only after the full (multi-minute, on local hardware) generation completes: one {@code token}
   * event per piece of generated text, followed by one {@code done} event carrying the final
   * persisted message (with citations) once generation finishes.
   */
  @PostMapping
  public void askQuestion(
      @PathVariable UUID notebookId,
      @Valid @RequestBody AskQuestionRequest request,
      HttpServletResponse response)
      throws IOException {
    response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
    response.setCharacterEncoding("UTF-8");
    try {
      ChatMessageResponse finalMessage =
          chatService.askQuestion(
              notebookId, request, token -> sendEvent(response, Map.of("token", token)));
      sendEvent(response, Map.of("done", true, "message", finalMessage));
    } catch (RuntimeException e) {
      // Once a token has streamed, the response is already committed at HTTP 200 — the status
      // line can't be changed. Signal failure inside the stream instead. If nothing has streamed
      // yet, rethrow so GlobalExceptionHandler can still return a normal JSON error response.
      if (response.isCommitted()) {
        sendEvent(response, Map.of("error", true, "message", "Failed to generate an answer."));
      } else {
        throw e;
      }
    }
  }

  private void sendEvent(HttpServletResponse response, Object payload) {
    String json = objectMapper.writeValueAsString(payload);
    try {
      response.getWriter().write("data: " + json + "\n\n");
      response.getWriter().flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
