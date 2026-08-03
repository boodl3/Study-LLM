package com.studyllm.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studyllm.ai.EmbeddingClient;
import com.studyllm.chat.dto.AskQuestionRequest;
import com.studyllm.common.NoReadySourcesException;
import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.Notebook;
import com.studyllm.notebook.NotebookRepository;
import com.studyllm.source.ChunkRepository;
import com.studyllm.source.Source;
import com.studyllm.source.SourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private NotebookRepository notebookRepository;
  @Mock private SourceRepository sourceRepository;
  @Mock private ChunkRepository chunkRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private EmbeddingClient embeddingClient;
  @Mock private OllamaChatClient ollamaChatClient;
  @Mock private OwnershipGuard ownershipGuard;

  private ChatService chatService;

  private final UUID userId = UUID.randomUUID();
  private final UUID notebookId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ChatProperties chatProperties = new ChatProperties(5, 0.6);
    chatService =
        new ChatService(
            notebookRepository,
            sourceRepository,
            chunkRepository,
            chatMessageRepository,
            embeddingClient,
            ollamaChatClient,
            chatProperties,
            ownershipGuard);
    when(ownershipGuard.currentUserId()).thenReturn(userId);
    when(notebookRepository.findByIdAndOwnerId(notebookId, userId))
        .thenReturn(Optional.of(new Notebook(userId, "Bio 101")));
  }

  @Test
  void askQuestion_throwsWhenNoReadySources() {
    when(sourceRepository.findByNotebookIdAndStatus(notebookId, Source.ProcessingStatus.READY))
        .thenReturn(List.of());

    assertThatThrownBy(
            () -> chatService.askQuestion(notebookId, new AskQuestionRequest("What's due?")))
        .isInstanceOf(NoReadySourcesException.class);

    verify(embeddingClient, never()).embed(anyString());
    verify(ollamaChatClient, never()).generate(anyString());
  }

  @Test
  void askQuestion_returnsNotFoundWhenNoChunkClearsThreshold() {
    when(sourceRepository.findByNotebookIdAndStatus(notebookId, Source.ProcessingStatus.READY))
        .thenReturn(List.of(new Source(notebookId, "syllabus.pdf", Source.FileType.PDF, 1000)));
    when(embeddingClient.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});
    when(chunkRepository.findNearestInReadySources(
            eq(notebookId), anyString(), anyDouble(), anyInt()))
        .thenReturn(List.of());

    var response =
        chatService.askQuestion(notebookId, new AskQuestionRequest("Capital of France?"));

    assertThat(response.notFoundInSources()).isTrue();
    assertThat(response.citedSources()).isEmpty();
    verify(ollamaChatClient, never()).generate(anyString());
  }

  @Test
  void askQuestion_excludesNonReadySourcesFromRetrieval() {
    // The zero-source guard only looks at READY sources — PROCESSING/FAILED ones don't count.
    when(sourceRepository.findByNotebookIdAndStatus(notebookId, Source.ProcessingStatus.READY))
        .thenReturn(List.of());

    assertThatThrownBy(
            () -> chatService.askQuestion(notebookId, new AskQuestionRequest("Anything?")))
        .isInstanceOf(NoReadySourcesException.class);

    verify(sourceRepository).findByNotebookIdAndStatus(notebookId, Source.ProcessingStatus.READY);
    verify(sourceRepository, never()).findByNotebookIdAndStatus(notebookId, Source.ProcessingStatus.PROCESSING);
    verify(sourceRepository, never()).findByNotebookIdAndStatus(notebookId, Source.ProcessingStatus.FAILED);
  }
}
