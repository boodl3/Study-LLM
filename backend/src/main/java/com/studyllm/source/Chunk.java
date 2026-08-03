package com.studyllm.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One retrievable slice of a source's extracted text, with its embedding for similarity search. */
@Entity
@Table(name = "chunks")
public class Chunk {

  @Id @GeneratedValue private UUID id;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private int position;

  @Column(name = "section_label")
  private String sectionLabel;

  @JdbcTypeCode(SqlTypes.VECTOR)
  @Array(length = 768)
  @Column(nullable = false)
  private float[] embedding;

  protected Chunk() {}

  public Chunk(UUID sourceId, String content, int position, String sectionLabel, float[] embedding) {
    this.sourceId = sourceId;
    this.content = content;
    this.position = position;
    this.sectionLabel = sectionLabel;
    this.embedding = embedding;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public String getContent() {
    return content;
  }

  public int getPosition() {
    return position;
  }

  public String getSectionLabel() {
    return sectionLabel;
  }

  public float[] getEmbedding() {
    return embedding;
  }
}
