package com.studyllm.notebook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookRepository extends JpaRepository<Notebook, UUID> {
  List<Notebook> findByOwnerIdOrderByLastActiveAtDesc(UUID ownerId);

  Optional<Notebook> findByIdAndOwnerId(UUID id, UUID ownerId);
}
