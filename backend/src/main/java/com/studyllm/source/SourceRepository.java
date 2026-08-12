package com.studyllm.source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {
  List<Source> findByNotebookIdOrderBySortOrderAscUploadedAtAsc(UUID notebookId);

  Optional<Source> findByIdAndNotebookId(UUID id, UUID notebookId);

  List<Source> findByNotebookIdAndStatus(UUID notebookId, Source.ProcessingStatus status);

  List<Source> findByNotebookIdAndFolderName(UUID notebookId, String folderName);

  int countByNotebookId(UUID notebookId);
}
