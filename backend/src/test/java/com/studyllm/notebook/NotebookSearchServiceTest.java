package com.studyllm.notebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.studyllm.common.OwnershipGuard;
import com.studyllm.source.ChunkRepository;
import com.studyllm.source.SourceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotebookSearchServiceTest {

  @Mock private NotebookRepository notebookRepository;
  @Mock private SourceRepository sourceRepository;
  @Mock private ChunkRepository chunkRepository;
  @Mock private OwnershipGuard ownershipGuard;

  private NotebookSearchService searchService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    searchService =
        new NotebookSearchService(notebookRepository, sourceRepository, chunkRepository, ownershipGuard);
    lenient().when(ownershipGuard.currentUserId()).thenReturn(userId);
  }

  @Test
  void search_withNoMatches_returnsEmptyResultSet() {
    Notebook notebook = new Notebook(userId, "Chemistry Notes");
    when(notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(userId)).thenReturn(List.of(notebook));
    lenient().when(chunkRepository.existsContentMatch(any(), anyString())).thenReturn(false);

    var result = searchService.search("nonsense query that matches nothing");

    assertThat(result.notebooks()).isEmpty();
  }

  @Test
  void search_matchesByTitle() {
    Notebook notebook = new Notebook(userId, "Chemistry Notes");
    when(notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(userId)).thenReturn(List.of(notebook));
    when(sourceRepository.countByNotebookId(notebook.getId())).thenReturn(0);

    var result = searchService.search("chemistry");

    assertThat(result.notebooks()).hasSize(1);
  }

  @Test
  void search_matchesBySourceContent() {
    Notebook notebook = new Notebook(userId, "Biology 101");
    when(notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(userId)).thenReturn(List.of(notebook));
    when(chunkRepository.existsContentMatch(eq(notebook.getId()), anyString())).thenReturn(true);
    when(sourceRepository.countByNotebookId(notebook.getId())).thenReturn(1);

    var result = searchService.search("mitochondria");

    assertThat(result.notebooks()).hasSize(1);
  }

  @Test
  void search_withBlankQuery_returnsEmptyWithoutQueryingRepositories() {
    var result = searchService.search("   ");

    assertThat(result.notebooks()).isEmpty();
  }
}
