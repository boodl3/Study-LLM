package com.studyllm.notebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.studyllm.common.NotFoundException;
import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.dto.CreateNotebookRequest;
import com.studyllm.notebook.dto.RenameNotebookRequest;
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
class NotebookServiceTest {

  @Mock private NotebookRepository notebookRepository;
  @Mock private SourceRepository sourceRepository;
  @Mock private OwnershipGuard ownershipGuard;

  private NotebookService notebookService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    notebookService = new NotebookService(notebookRepository, sourceRepository, ownershipGuard);
    lenient().when(ownershipGuard.currentUserId()).thenReturn(userId);
    lenient()
        .when(notebookRepository.save(org.mockito.ArgumentMatchers.any(Notebook.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void create_rejectsBlankTitle() {
    assertThatThrownBy(() -> notebookService.create(new CreateNotebookRequest("   ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void create_trimsTitle() {
    var dto = notebookService.create(new CreateNotebookRequest("  Bio 101  "));
    assertThat(dto.title()).isEqualTo("Bio 101");
  }

  @Test
  void rename_rejectsBlankTitle() {
    UUID notebookId = UUID.randomUUID();
    assertThatThrownBy(
            () -> notebookService.rename(notebookId, new RenameNotebookRequest(""))
        )
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rename_throwsNotFoundWhenNotOwned() {
    UUID notebookId = UUID.randomUUID();
    when(notebookRepository.findByIdAndOwnerId(notebookId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> notebookService.rename(notebookId, new RenameNotebookRequest("New title")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void list_isScopedToCurrentOwnerAndOrderedByLastActive() {
    notebookService.list();
    org.mockito.Mockito.verify(notebookRepository).findByOwnerIdOrderByLastActiveAtDesc(userId);
  }

  @Test
  void delete_throwsNotFoundWhenNotOwned() {
    UUID notebookId = UUID.randomUUID();
    when(notebookRepository.findByIdAndOwnerId(notebookId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> notebookService.delete(notebookId)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void get_returnsSourceCountFromRepository() {
    UUID notebookId = UUID.randomUUID();
    Notebook notebook = new Notebook(userId, "Bio 101");
    when(notebookRepository.findByIdAndOwnerId(notebookId, userId)).thenReturn(Optional.of(notebook));
    when(sourceRepository.countByNotebookId(notebook.getId())).thenReturn(3);

    var dto = notebookService.get(notebookId);

    assertThat(dto.sourceCount()).isEqualTo(3);
  }

  @Test
  void list_mapsEachNotebookToDto() {
    Notebook n1 = new Notebook(userId, "A");
    Notebook n2 = new Notebook(userId, "B");
    when(notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(userId)).thenReturn(List.of(n1, n2));

    var response = notebookService.list();

    assertThat(response.notebooks()).hasSize(2);
  }
}
