package com.studyllm.notebook;

import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.dto.NotebookDto;
import com.studyllm.notebook.dto.NotebookListResponse;
import com.studyllm.source.ChunkRepository;
import com.studyllm.source.SourceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matches notebook titles and the content of their sources (spec FR-007, FR-008, FR-009),
 * scoped to the caller's own notebooks. */
@Service
public class NotebookSearchService {

  private final NotebookRepository notebookRepository;
  private final SourceRepository sourceRepository;
  private final ChunkRepository chunkRepository;
  private final OwnershipGuard ownershipGuard;

  public NotebookSearchService(
      NotebookRepository notebookRepository,
      SourceRepository sourceRepository,
      ChunkRepository chunkRepository,
      OwnershipGuard ownershipGuard) {
    this.notebookRepository = notebookRepository;
    this.sourceRepository = sourceRepository;
    this.chunkRepository = chunkRepository;
    this.ownershipGuard = ownershipGuard;
  }

  /**
   * Returns the caller's notebooks whose title or source content matches the query; a blank
   * query returns no results rather than everything.
   */
  @Transactional
  public NotebookListResponse search(String query) {
    String term = query == null ? "" : query.trim();
    if (term.isEmpty()) {
      return new NotebookListResponse(List.of());
    }
    String lowerTerm = term.toLowerCase();
    return new NotebookListResponse(
        notebookRepository.findByOwnerIdOrderByLastActiveAtDesc(ownershipGuard.currentUserId()).stream()
            .filter(
                n ->
                    n.getTitle().toLowerCase().contains(lowerTerm)
                        || chunkRepository.existsContentMatch(n.getId(), term))
            .map(this::toDto)
            .toList());
  }

  private NotebookDto toDto(Notebook notebook) {
    int sourceCount = sourceRepository.countByNotebookId(notebook.getId());
    return new NotebookDto(
        notebook.getId(), notebook.getTitle(), sourceCount, notebook.getLastActiveAt());
  }
}
