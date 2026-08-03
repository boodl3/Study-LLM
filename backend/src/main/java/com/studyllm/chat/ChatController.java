package com.studyllm.chat;

import com.studyllm.chat.dto.AskQuestionRequest;
import com.studyllm.chat.dto.ChatHistoryResponse;
import com.studyllm.chat.dto.ChatMessageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for a notebook's chat: reading history and asking a grounded question. */
@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/chat")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @GetMapping
  public ChatHistoryResponse getHistory(@PathVariable UUID notebookId) {
    return chatService.getHistory(notebookId);
  }

  @PostMapping
  public ChatMessageResponse askQuestion(
      @PathVariable UUID notebookId, @Valid @RequestBody AskQuestionRequest request) {
    return chatService.askQuestion(notebookId, request);
  }
}
