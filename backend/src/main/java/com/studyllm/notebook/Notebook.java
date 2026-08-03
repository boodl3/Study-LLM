package com.studyllm.notebook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A user's notebook: a titled container for sources and their chat history. */
@Entity
@Table(name = "notebooks")
public class Notebook {

  @Id @GeneratedValue private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String title;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "last_active_at", nullable = false)
  private Instant lastActiveAt = Instant.now();

  protected Notebook() {}

  public Notebook(UUID ownerId, String title) {
    this.ownerId = ownerId;
    this.title = title;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastActiveAt() {
    return lastActiveAt;
  }

  /** Bumps {@code lastActiveAt} to now, used to keep the notebook list sorted by recent activity. */
  public void touch() {
    this.lastActiveAt = Instant.now();
  }
}
