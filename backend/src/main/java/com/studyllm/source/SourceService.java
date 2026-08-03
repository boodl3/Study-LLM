package com.studyllm.source;

import com.studyllm.common.NotFoundException;
import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.Notebook;
import com.studyllm.notebook.NotebookRepository;
import com.studyllm.source.dto.SourceDto;
import com.studyllm.source.dto.SourceListResponse;
import com.studyllm.source.dto.UploadResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SourceService {

  private final NotebookRepository notebookRepository;
  private final SourceRepository sourceRepository;
  private final SourceIngestionPipeline ingestionPipeline;
  private final OwnershipGuard ownershipGuard;
  private final long maxFileSizeBytes;
  private final Tika tika = new Tika();

  public SourceService(
      NotebookRepository notebookRepository,
      SourceRepository sourceRepository,
      SourceIngestionPipeline ingestionPipeline,
      OwnershipGuard ownershipGuard,
      @Value("${studyllm.upload.max-file-size-bytes}") long maxFileSizeBytes) {
    this.notebookRepository = notebookRepository;
    this.sourceRepository = sourceRepository;
    this.ingestionPipeline = ingestionPipeline;
    this.ownershipGuard = ownershipGuard;
    this.maxFileSizeBytes = maxFileSizeBytes;
  }

  @Transactional
  public SourceListResponse list(UUID notebookId) {
    requireOwnedNotebook(notebookId);
    return new SourceListResponse(
        sourceRepository.findByNotebookIdOrderByUploadedAtDesc(notebookId).stream()
            .map(this::toDto)
            .toList());
  }

  @Transactional
  public SourceDto get(UUID notebookId, UUID sourceId) {
    requireOwnedNotebook(notebookId);
    return toDto(
        sourceRepository
            .findByIdAndNotebookId(sourceId, notebookId)
            .orElseThrow(() -> new NotFoundException("Source not found")));
  }

  // Deliberately not @Transactional: the async ingestion pipeline dispatched below runs on
  // another thread against its own connection, and must only see the just-saved Source row
  // once it's actually committed — which requires each save here to commit on its own rather
  // than all sharing one transaction that doesn't commit until this method returns.
  public UploadResponse upload(UUID notebookId, MultipartFile file) {
    Notebook notebook = requireOwnedNotebook(notebookId);

    byte[] content = readBytes(file);
    if (content.length > maxFileSizeBytes) {
      throw new IllegalArgumentException("File exceeds the 50MB limit");
    }
    Source.FileType fileType = detectFileType(content, file.getOriginalFilename());

    Source source =
        sourceRepository.save(
            new Source(notebookId, file.getOriginalFilename(), fileType, content.length));
    notebook.touch();
    notebookRepository.save(notebook);

    ingestionPipeline.process(source.getId(), content, fileType);

    return new UploadResponse(source.getId(), source.getFilename(), source.getStatus());
  }

  private Source.FileType detectFileType(byte[] content, String filename) {
    String mime = tika.detect(content);
    return switch (mime) {
      case "application/pdf" -> Source.FileType.PDF;
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
          Source.FileType.DOCX;
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
          Source.FileType.PPTX;
      default -> {
        if (mime.startsWith("text/")) {
          yield filename != null && filename.toLowerCase().endsWith(".md")
              ? Source.FileType.MD
              : Source.FileType.TXT;
        }
        throw new IllegalArgumentException("Unsupported file type: " + mime);
      }
    };
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read uploaded file", e);
    }
  }

  private Notebook requireOwnedNotebook(UUID notebookId) {
    return notebookRepository
        .findByIdAndOwnerId(notebookId, ownershipGuard.currentUserId())
        .orElseThrow(() -> new NotFoundException("Notebook not found"));
  }

  private SourceDto toDto(Source source) {
    return new SourceDto(
        source.getId(),
        source.getFilename(),
        source.getFileType(),
        source.getStatus(),
        source.getFailureReason(),
        source.getUploadedAt());
  }
}
