package com.studyllm.source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {
  List<Source> findByNotebookIdOrderByUploadedAtDesc(UUID notebookId);

  Optional<Source> findByIdAndNotebookId(UUID id, UUID notebookId);

  List<Source> findByNotebookIdAndStatus(UUID notebookId, Source.ProcessingStatus status);

  int countByNotebookId(UUID notebookId);
}
