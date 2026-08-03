package com.studyllm.source;

/** An unembedded chunk, produced by {@link RecursiveChunker} before {@code EmbeddingClient} runs. */
public record ChunkDraft(String content, int position, String sectionLabel) {}
