package com.studyllm.source;

import com.studyllm.common.NotFoundException;
import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.Notebook;
import com.studyllm.notebook.NotebookRepository;
import com.studyllm.source.dto.RenameSourceRequest;
import com.studyllm.source.dto.SourceDto;
import com.studyllm.source.dto.SourceListResponse;
import com.studyllm.source.dto.UploadResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** CRUD for sources within a notebook, plus dispatching newly uploaded files to async ingestion. */
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

  /** Lists a notebook's sources in display order. */
  @Transactional
  public SourceListResponse list(UUID notebookId) {
    requireOwnedNotebook(notebookId);
    return new SourceListResponse(
        sourceRepository.findByNotebookIdOrderBySortOrderAscUploadedAtAsc(notebookId).stream()
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

  /**
   * Validates and stores an uploaded file, then kicks off async ingestion (extract/chunk/embed)
   * and returns immediately with the new source in PROCESSING status.
   *
   * <p>Deliberately not @Transactional: the async ingestion pipeline dispatched below runs on
   * another thread against its own connection, and must only see the just-saved Source row
   * once it's actually committed — which requires each save here to commit on its own rather
   * than all sharing one transaction that doesn't commit until this method returns.
   */
  public UploadResponse upload(UUID notebookId, MultipartFile file) {
    return upload(notebookId, file, null);
  }

  /** Same as {@link #upload(UUID, MultipartFile)}, tagging the source with its source folder. */
  public UploadResponse upload(UUID notebookId, MultipartFile file, String folderName) {
    Notebook notebook = requireOwnedNotebook(notebookId);

    byte[] content = readBytes(file);
    if (content.length > maxFileSizeBytes) {
      throw new IllegalArgumentException("File exceeds the 50MB limit");
    }
    Source.FileType fileType = detectFileType(content, file.getOriginalFilename());

    // ponytail: next sort_order is derived from the current count, so it can collide after a
    // delete-then-upload race; the ORDER BY tiebreaks on uploaded_at so display stays sane.
    // Upgrade to a DB sequence if concurrent uploads to the same notebook become common.
    int sortOrder = sourceRepository.countByNotebookId(notebookId);
    Source source =
        sourceRepository.save(
            new Source(
                notebookId,
                file.getOriginalFilename(),
                fileType,
                content.length,
                blankToNull(folderName),
                sortOrder));
    notebook.touch();
    notebookRepository.save(notebook);

    ingestionPipeline.process(source.getId(), content, fileType);

    return new UploadResponse(source.getId(), source.getFilename(), source.getStatus());
  }

  /**
   * Persists a new display order for the notebook's sources. Ids not owned by the notebook are
   * ignored; any of the notebook's sources missing from the list keep their existing order and
   * sort after the reordered ones.
   */
  @Transactional
  public SourceListResponse reorder(UUID notebookId, List<UUID> orderedIds) {
    requireOwnedNotebook(notebookId);
    List<Source> sources =
        sourceRepository.findByNotebookIdOrderBySortOrderAscUploadedAtAsc(notebookId);
    Map<UUID, Source> byId = new HashMap<>();
    for (Source source : sources) {
      byId.put(source.getId(), source);
    }

    int order = 0;
    for (UUID id : orderedIds) {
      Source source = byId.remove(id);
      if (source != null) {
        source.setSortOrder(order++);
      }
    }
    for (Source leftover : byId.values()) {
      leftover.setSortOrder(order++);
    }
    sourceRepository.saveAll(sources);

    return list(notebookId);
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Renames a source in place; the file content and ingestion state are untouched. */
  @Transactional
  public SourceDto rename(UUID notebookId, UUID sourceId, RenameSourceRequest request) {
    requireOwnedNotebook(notebookId);
    String filename = requireNonBlankFilename(request.filename());
    Source source =
        sourceRepository
            .findByIdAndNotebookId(sourceId, notebookId)
            .orElseThrow(() -> new NotFoundException("Source not found"));
    source.setFilename(filename);
    return toDto(sourceRepository.save(source));
  }

  /** Deletes a source and (via cascade) its chunks. */
  @Transactional
  public void delete(UUID notebookId, UUID sourceId) {
    requireOwnedNotebook(notebookId);
    Source source =
        sourceRepository
            .findByIdAndNotebookId(sourceId, notebookId)
            .orElseThrow(() -> new NotFoundException("Source not found"));
    sourceRepository.delete(source);
  }

  /** Renames a folder by retagging every source that carries its folderName. */
  @Transactional
  public SourceListResponse renameFolder(UUID notebookId, String folderName, String newFolderName) {
    requireOwnedNotebook(notebookId);
    String trimmed = requireNonBlankFilename(newFolderName);
    List<Source> sources = requireFolderSources(notebookId, folderName);
    sources.forEach(source -> source.setFolderName(trimmed));
    sourceRepository.saveAll(sources);
    return list(notebookId);
  }

  /** Deletes every source in a folder (and, via cascade, their chunks). */
  @Transactional
  public void deleteFolder(UUID notebookId, String folderName) {
    requireOwnedNotebook(notebookId);
    sourceRepository.deleteAll(requireFolderSources(notebookId, folderName));
  }

  private List<Source> requireFolderSources(UUID notebookId, String folderName) {
    List<Source> sources = sourceRepository.findByNotebookIdAndFolderName(notebookId, folderName);
    if (sources.isEmpty()) {
      throw new NotFoundException("Folder not found");
    }
    return sources;
  }

  private static String requireNonBlankFilename(String filename) {
    String trimmed = filename == null ? "" : filename.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Filename must not be blank");
    }
    return trimmed;
  }

  /** Sniffs the real file type from content (via Tika) rather than trusting the extension. */
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
        source.getUploadedAt(),
        source.getFolderName());
  }
}
