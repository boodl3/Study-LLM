package com.studyllm.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sources")
public class Source {

  public enum FileType {
    PDF,
    DOCX,
    PPTX,
    TXT,
    MD
  }

  public enum ProcessingStatus {
    PROCESSING,
    READY,
    FAILED
  }

  @Id @GeneratedValue private UUID id;

  @Column(name = "notebook_id", nullable = false)
  private UUID notebookId;

  @Column(nullable = false)
  private String filename;

  @Enumerated(EnumType.STRING)
  @Column(name = "file_type", nullable = false)
  private FileType fileType;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProcessingStatus status;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt = Instant.now();

  @Column(name = "ready_at")
  private Instant readyAt;

  protected Source() {}

  public Source(UUID notebookId, String filename, FileType fileType, long fileSizeBytes) {
    this.notebookId = notebookId;
    this.filename = filename;
    this.fileType = fileType;
    this.fileSizeBytes = fileSizeBytes;
    this.status = ProcessingStatus.PROCESSING;
  }

  public void markReady() {
    this.status = ProcessingStatus.READY;
    this.readyAt = Instant.now();
  }

  public void markFailed(String reason) {
    this.status = ProcessingStatus.FAILED;
    this.failureReason = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public FileType getFileType() {
    return fileType;
  }

  public long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public ProcessingStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getReadyAt() {
    return readyAt;
  }
}
