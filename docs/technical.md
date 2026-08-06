# Technical Documentation

For developers working in the codebase: API reference, algorithms, database
structure, and configuration. See [architecture.md](architecture.md) for the
system-level view this drills into.

## API reference

Base path: `/api/v1`. All endpoints except `/auth/*` require an
`Authorization: Bearer <jwt>` header; requests are scoped to the token's
user id (never a client-supplied id). JSON in/out except upload, which is
`multipart/form-data`.

### Auth — `com.studyllm.auth`

| Method | Path | Body | Response | Notes |
|---|---|---|---|---|
| POST | `/auth/signup` | `{username, email, password, confirmPassword}` | `201` `AuthResponse` | Rejects duplicate username/email (`409`) or mismatched passwords (`400`). |
| POST | `/auth/login` | `{identifier, password}` | `200` `AuthResponse` | `identifier` is username or email. `401` on bad credentials. |
| PATCH | `/users/me` | `{username, email}` | `200` `UserDto` | Update profile; `409` if either is taken. |
| PUT | `/users/me/password` | `{currentPassword, newPassword, confirmNewPassword}` | `204` | `401` if `currentPassword` is wrong. |

### Notebooks — `com.studyllm.notebook`

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/notebooks` | — | `NotebookListResponse`, most-recently-active first |
| GET | `/notebooks/search?q=` | — | `NotebookListResponse` matching title or source content; blank `q` returns `[]` |
| GET | `/notebooks/{id}` | — | `NotebookDto` |
| POST | `/notebooks` | `{title}` | `201` `NotebookDto` |
| PATCH | `/notebooks/{id}` | `{title}` | `200` `NotebookDto` |
| DELETE | `/notebooks/{id}` | — | `204`; cascades to sources, chunks, chat history |

### Sources — `com.studyllm.source`

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/notebooks/{notebookId}/sources` | — | `SourceListResponse`, most recently uploaded first |
| GET | `/notebooks/{notebookId}/sources/{sourceId}` | — | `SourceDto` |
| POST | `/notebooks/{notebookId}/sources` | multipart `file` | `202` `UploadResponse` (status `PROCESSING`); ingestion continues async |
| PATCH | `/notebooks/{notebookId}/sources/{sourceId}` | `{filename}` | `200` `SourceDto` |
| DELETE | `/notebooks/{notebookId}/sources/{sourceId}` | — | `204`; cascades to chunks |

### Chat — `com.studyllm.chat`

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/notebooks/{notebookId}/chat` | — | `ChatHistoryResponse`, oldest first |
| POST | `/notebooks/{notebookId}/chat` | `{question}` | `ChatMessageResponse` (answer + cited sources); `422` if the notebook has zero `READY` sources |

### Error shape

All non-2xx responses return `{code, message}` (`common.ErrorResponse`).
Mapping lives in `common.GlobalExceptionHandler`:

| HTTP | `code` | Thrown by |
|---|---|---|
| 400 | `BAD_REQUEST` | Bean validation / `IllegalArgumentException` |
| 401 | `UNAUTHORIZED` | `BadCredentialsException` |
| 404 | `NOT_FOUND` | `NotFoundException` |
| 409 | `CONFLICT` | `ConflictException` (duplicate username/email) |
| 422 | `NO_READY_SOURCES` | `NoReadySourcesException` |
| 500 | `INTERNAL_ERROR` | anything unhandled (logged server-side, message not leaked) |

## Algorithms

### File type detection

`SourceService.detectFileType` sniffs the real MIME type from file **content**
via Apache Tika (never trusts the extension), then maps it to
`PDF`/`DOCX`/`PPTX`/`TXT`/`MD`. `.md` vs `.txt` is disambiguated by extension
only after Tika confirms the content is `text/*`. Anything else is rejected.

### Text extraction

`TextExtractorFactory` dispatches to one `TextExtractor` per format
(`PdfTextExtractor` — PDFBox; `DocxTextExtractor`/`PptxTextExtractor` — Apache
POI; `PlainTextExtractor` — direct UTF-8 read). Each returns a list of
`ExtractedSection` (text + an optional label — page number, slide number —
used later for citations).

### Recursive chunking — `RecursiveChunker`

Structure-aware, priority-ordered splitting, greedily packed into
1,200–2,000-character chunks (~300–500 tokens) with ~12.5% overlap:

1. Split each extracted section on blank lines (paragraphs).
2. A paragraph over the max size is split on sentence boundaries
   (`(?<=[.!?])\s+`).
3. A sentence still over the max size is hard-split at the character limit.
4. Units are appended to a buffer; when the next unit would push the buffer
   past 2,000 chars, the buffer is flushed as a chunk and reseeded with the
   trailing ~12.5% of the flushed chunk (word-boundary aligned) so adjacent
   chunks retain context.

No chunking framework — this is a self-contained, directly unit-tested
algorithm (`RecursiveChunkerTest`).

### Retrieval and grounded generation — `ChatService`

1. Reject if the notebook has zero `READY` sources (`422`).
2. Embed the question via `EmbeddingClient` (Ollama `nomic-embed-text`,
   768-dim).
3. `ChunkRepository.findNearestInReadySources` — cosine distance (`<=>`,
   pgvector) over chunks belonging to `READY` sources only, filtered to
   `relevance-max-distance` (default `0.6`) and limited to
   `retrieval-top-k` (default `5`).
4. No chunks within threshold → fixed "not found in sources" answer, **no
   LLM call**.
5. Otherwise, build a prompt that wraps the retrieved excerpts in a
   `<source_excerpts>` block, instructs the model to answer only from that
   block and to treat it as data rather than instructions (prompt-injection
   guard), and send it to Ollama (`qwen3:8b`) via `OllamaChatClient`.
6. Persist both the user's question and the assistant's answer
   (`ChatMessage`), with cited chunk ids on grounded answers.

## Database structure

PostgreSQL 16+ with the `pgvector` extension. Full ERD and column-level
detail: [README → Data model](../README.md#data-model). Migrations:
[`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration).

- `users`, `notebooks`, `sources`, `chunks`, `chat_messages` — see the ERD.
- `chunks.embedding` is `vector(768)`, indexed with an HNSW index
  (`vector_cosine_ops`) for approximate nearest-neighbor search.
- All child tables cascade-delete from their parent (`ON DELETE CASCADE`) —
  deleting a notebook removes its sources, chunks, and chat history in one
  statement.
- `chat_messages.cited_chunk_ids` is a plain `uuid[]` (no FK constraint);
  resolved to source filenames/sections in `ChatService.toResponse` at read
  time.

## Configuration

Full environment variable table (required vs. optional, defaults):
[README → Environment variables](../README.md#environment-variables).

Additional non-env-var settings, in `application.yml`:

| Setting | Default | Purpose |
|---|---|---|
| `studyllm.chat.retrieval-top-k` | `5` | Max chunks retrieved per question |
| `studyllm.chat.relevance-max-distance` | `0.6` | Cosine-distance cutoff — chunks farther than this are treated as irrelevant |
| `studyllm.upload.max-file-size-bytes` | `52428800` (50MB) | Enforced in `SourceService.upload`, ahead of Spring's own `55MB` multipart ceiling |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema is Flyway-owned; Hibernate only validates it matches |

## Testing

| Layer | Stack | Location |
|---|---|---|
| Backend unit | JUnit 5, Mockito | `backend/src/test/java/.../{auth,chat,notebook,source}` |
| Backend integration | Spring Boot Test + Testcontainers (Postgres+pgvector) | `backend/src/test/java/.../integration` |
| Frontend | Vitest, React Testing Library | configured via `frontend/src/setupTests.ts`; no component tests committed yet |

Run commands: [README → Testing](../README.md#testing).
