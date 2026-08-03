package com.studyllm.notebook;

import com.studyllm.common.NotFoundException;
import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.dto.CreateNotebookRequest;
import com.studyllm.notebook.dto.NotebookDto;
import com.studyllm.notebook.dto.NotebookListResponse;
import com.studyllm.notebook.dto.RenameNotebookRequest;
import com.studyllm.source.SourceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotebookService {

  private final NotebookRepository notebookRepository;
  private final SourceRepository sourceRepository;
  private final OwnershipGuard ownershipGuard;

  public NotebookService(
      NotebookRepository notebookRepository,
      SourceRepository sourceRepository,
      OwnershipGuard ownershipGuard) {
    this.notebookRepository = notebookRepository;
    this.sourceRepository = sourceRepository;
    this.ownershipGuard = ownershipGuard;
  }

  @Transactional
  public NotebookListResponse list() {
    UUID userId = ownershipGuard.currentUserId();
    return new NotebookListResponse(
        notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(userId).stream()
            .map(this::toDto)
            .toList());
  }

  @Transactional
  public NotebookDto get(UUID id) {
    return toDto(requireOwnedNotebook(id));
  }

  @Transactional
  public NotebookDto create(CreateNotebookRequest request) {
    String title = requireNonBlankTitle(request.title());
    Notebook notebook = notebookRepository.save(new Notebook(ownershipGuard.currentUserId(), title));
    return toDto(notebook);
  }

  @Transactional
  public NotebookDto rename(UUID id, RenameNotebookRequest request) {
    String title = requireNonBlankTitle(request.title());
    Notebook notebook = requireOwnedNotebook(id);
    notebook.setTitle(title);
    notebook.touch();
    return toDto(notebookRepository.save(notebook));
  }

  @Transactional
  public void delete(UUID id) {
    Notebook notebook = requireOwnedNotebook(id);
    notebookRepository.delete(notebook);
  }

  private Notebook requireOwnedNotebook(UUID id) {
    return notebookRepository
        .findByIdAndOwnerId(id, ownershipGuard.currentUserId())
        .orElseThrow(() -> new NotFoundException("Notebook not found"));
  }

  private static String requireNonBlankTitle(String title) {
    String trimmed = title == null ? "" : title.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    return trimmed;
  }

  private NotebookDto toDto(Notebook notebook) {
    int sourceCount = sourceRepository.countByNotebookId(notebook.getId());
    return new NotebookDto(
        notebook.getId(), notebook.getTitle(), sourceCount, notebook.getLastActiveAt());
  }
}
