package com.studyllm.source;

import com.studyllm.source.dto.RenameSourceRequest;
import com.studyllm.source.dto.SourceDto;
import com.studyllm.source.dto.SourceListResponse;
import com.studyllm.source.dto.UploadResponse;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/notebooks/{notebookId}/sources")
public class SourceController {

  private final SourceService sourceService;

  public SourceController(SourceService sourceService) {
    this.sourceService = sourceService;
  }

  @GetMapping
  public SourceListResponse list(@PathVariable UUID notebookId) {
    return sourceService.list(notebookId);
  }

  @GetMapping("/{sourceId}")
  public SourceDto get(@PathVariable UUID notebookId, @PathVariable UUID sourceId) {
    return sourceService.get(notebookId, sourceId);
  }

  @PostMapping
  public ResponseEntity<UploadResponse> upload(
      @PathVariable UUID notebookId, @RequestParam("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(sourceService.upload(notebookId, file));
  }

  @PatchMapping("/{sourceId}")
  public SourceDto rename(
      @PathVariable UUID notebookId,
      @PathVariable UUID sourceId,
      @Valid @RequestBody RenameSourceRequest request) {
    return sourceService.rename(notebookId, sourceId, request);
  }

  @DeleteMapping("/{sourceId}")
  public ResponseEntity<Void> delete(@PathVariable UUID notebookId, @PathVariable UUID sourceId) {
    sourceService.delete(notebookId, sourceId);
    return ResponseEntity.noContent().build();
  }
}
