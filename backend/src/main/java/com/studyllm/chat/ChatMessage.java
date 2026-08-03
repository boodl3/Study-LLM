package com.studyllm.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

  public enum Role {
    USER,
    ASSISTANT
  }

  @Id @GeneratedValue private UUID id;

  @Column(name = "notebook_id", nullable = false)
  private UUID notebookId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "cited_chunk_ids")
  private UUID[] citedChunkIds;

  @Column(name = "not_found_in_sources", nullable = false)
  private boolean notFoundInSources;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected ChatMessage() {}

  public static ChatMessage userMessage(UUID notebookId, String content) {
    ChatMessage m = new ChatMessage();
    m.notebookId = notebookId;
    m.role = Role.USER;
    m.content = content;
    return m;
  }

  public static ChatMessage assistantMessage(
      UUID notebookId, String content, UUID[] citedChunkIds, boolean notFoundInSources) {
    ChatMessage m = new ChatMessage();
    m.notebookId = notebookId;
    m.role = Role.ASSISTANT;
    m.content = content;
    m.citedChunkIds = citedChunkIds;
    m.notFoundInSources = notFoundInSources;
    return m;
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public Role getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public UUID[] getCitedChunkIds() {
    return citedChunkIds;
  }

  public boolean isNotFoundInSources() {
    return notFoundInSources;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
