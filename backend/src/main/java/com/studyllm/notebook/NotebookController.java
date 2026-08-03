package com.studyllm.notebook;

import com.studyllm.notebook.dto.CreateNotebookRequest;
import com.studyllm.notebook.dto.NotebookDto;
import com.studyllm.notebook.dto.NotebookListResponse;
import com.studyllm.notebook.dto.RenameNotebookRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notebooks")
public class NotebookController {

  private final NotebookService notebookService;
  private final NotebookSearchService notebookSearchService;

  public NotebookController(NotebookService notebookService, NotebookSearchService notebookSearchService) {
    this.notebookService = notebookService;
    this.notebookSearchService = notebookSearchService;
  }

  @GetMapping
  public NotebookListResponse list() {
    return notebookService.list();
  }

  @GetMapping("/search")
  public NotebookListResponse search(@RequestParam(defaultValue = "") String q) {
    return notebookSearchService.search(q);
  }

  @GetMapping("/{id}")
  public NotebookDto get(@PathVariable UUID id) {
    return notebookService.get(id);
  }

  @PostMapping
  public ResponseEntity<NotebookDto> create(@Valid @RequestBody CreateNotebookRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(notebookService.create(request));
  }

  @PatchMapping("/{id}")
  public NotebookDto rename(@PathVariable UUID id, @Valid @RequestBody RenameNotebookRequest request) {
    return notebookService.rename(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    notebookService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
