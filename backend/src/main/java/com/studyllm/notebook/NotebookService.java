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

/** CRUD for notebooks, scoped to whichever user is authenticated on the current request. */
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

  /** Lists the current user's notebooks, most recently active first. */
  @Transactional
  public NotebookListResponse list() {
    UUID userId = ownershipGuard.currentUserId();
    return new NotebookListResponse(
        notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(userId).stream()
            .map(this::toDto)
            .toList());
  }

  /** Fetches one notebook by ID, provided the current user owns it. */
  @Transactional
  public NotebookDto get(UUID id) {
    return toDto(requireOwnedNotebook(id));
  }

  /** Creates a new notebook owned by the current user. */
  @Transactional
  public NotebookDto create(CreateNotebookRequest request) {
    String title = requireNonBlankTitle(request.title());
    Notebook notebook = notebookRepository.save(new Notebook(ownershipGuard.currentUserId(), title));
    return toDto(notebook);
  }

  /** Renames a notebook and bumps its last-active timestamp. */
  @Transactional
  public NotebookDto rename(UUID id, RenameNotebookRequest request) {
    String title = requireNonBlankTitle(request.title());
    Notebook notebook = requireOwnedNotebook(id);
    notebook.setTitle(title);
    notebook.touch();
    return toDto(notebookRepository.save(notebook));
  }

  /** Deletes a notebook and (via cascade) its sources, chunks, and chat history. */
  @Transactional
  public void delete(UUID id) {
    Notebook notebook = requireOwnedNotebook(id);
    notebookRepository.delete(notebook);
  }

  /** Looks up the notebook, scoped to the current user, or throws 404 if absent/not owned. */
  private Notebook requireOwnedNotebook(UUID id) {
    return notebookRepository
        .findByIdAndOwnerId(id, ownershipGuard.currentUserId())
        .orElseThrow(() -> new NotFoundException("Notebook not found"));
  }

  /** Trims a requested title and rejects it if that leaves nothing. */
  private static String requireNonBlankTitle(String title) {
    String trimmed = title == null ? "" : title.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Title must not be blank");
    }
    return trimmed;
  }

  /** Maps an entity to its response DTO, including a freshly counted source total. */
  private NotebookDto toDto(Notebook notebook) {
    int sourceCount = sourceRepository.countByNotebookId(notebook.getId());
    return new NotebookDto(
        notebook.getId(), notebook.getTitle(), sourceCount, notebook.getLastActiveAt());
  }
}
