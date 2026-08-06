# Requirements Documentation

What Study LLM must do, the behavior it must guarantee, and the environment it
runs in. Source of truth for developers and testers; see
[architecture.md](architecture.md) for how it's built and
[technical.md](technical.md) for implementation detail.

## Product summary

A student organizes course materials into **Notebooks**, uploads source
documents into a notebook, and asks an AI assistant questions that are
answered **strictly** from that notebook's sources — with citations, or an
explicit "not found" response when the sources don't cover the question.

## Functional requirements

| ID | Requirement |
|---|---|
| FR-001 | The Home page is the application's entry point. |
| FR-002 | Home lists the current user's notebooks, most-recently-active first. |
| FR-003 | Home shows an empty state when the user has no notebooks. |
| FR-004 | Users can create a notebook; it appears on Home as the most recent entry. |
| FR-005 | Users can rename a notebook's title in place; an empty title is rejected. |
| FR-006 | Users can delete a notebook (with confirmation); deletion permanently removes the notebook, its sources, and its chat history. |
| FR-007 | Home has a search bar that filters notebooks by title. |
| FR-008 | Search also matches uploaded-source content, returning the parent notebook. |
| FR-009 | A search that matches nothing shows a clear no-results state. |
| FR-010 | Users can upload source documents into a notebook. |
| FR-011 | Each source shows its processing status (processing / ready / failed) and, on failure, a reason. |
| FR-012 | Only successfully processed (`READY`) sources are used by the assistant; `PROCESSING`/`FAILED` sources are excluded. |
| FR-013 | Accepted upload formats: PDF, DOCX, PPTX, TXT, MD, up to 50MB each; anything else is rejected with a clear reason. |
| FR-014 | Users can ask natural-language questions about a notebook's sources via chat. |
| FR-015 | The assistant answers strictly from the notebook's `READY` sources — never from its own general knowledge. |
| FR-016 | Every grounded answer indicates which source(s) it drew from, down to page/section where that structure is extractable (PDF/DOCX/PPTX); source-only citation is sufficient for TXT/MD. |
| FR-017 | When no available source is relevant, the assistant states it couldn't find the answer rather than guessing. |
| FR-018 | A notebook with zero `READY` sources blocks question submission and prompts the user to upload one. |
| FR-019 | Users sign up with email + password and log in; a notebook's sources and chat history are visible only to its owning account. Email verification and password reset are out of scope. |
| FR-020 | Notebook create/rename/delete are reflected on Home immediately, with no stale or duplicate entries. |
| FR-021 | If a notebook is deleted while a question against it is in flight, that request fails gracefully rather than returning a result. |
| FR-022 | Home and all notebooks are reachable only by a signed-in user, scoped to their own account. |

## Non-functional requirements

| Category | Requirement |
|---|---|
| Performance | Source upload → `READY` within 2 minutes (SC-006), including the 50MB size ceiling. |
| Performance | Chat answer returned within 15 seconds (SC-007). |
| Security | Every notebook/source/chat query is scoped to the authenticated caller (JWT-derived user id) — never trusted from client input. |
| Security | Uploads are validated by content-sniffing (not file extension) before parsing. |
| Security | Retrieved source excerpts are delimited from system/instruction text in the LLM prompt, so document content can't act as instructions to the model. |
| Security | Passwords are hashed (BCrypt); auth is stateless (JWT bearer, no server-side session store). |
| Reliability | A failed source only fails that source's status — never the notebook or an in-flight chat. |
| Reliability | `/actuator/health` is exposed for liveness checks. |
| Correctness | 100% of assistant answers either cite a supporting source or explicitly state the information wasn't found — never presented as fact without one or the other (SC-003). |
| Usability | Home and source lists stay usable (scrollable) with no cap on notebook or source count. |

## Operating environment

- **Server**: JVM 21, Spring Boot, any OS that runs Docker/Postgres.
- **Database**: PostgreSQL 16+ with the `pgvector` extension.
- **Model runtime**: [Ollama](https://ollama.com) running locally with `qwen3:8b` (chat) and `nomic-embed-text` (embeddings) pulled — no cloud LLM vendor or API key.
- **Client**: any evergreen browser; dark-mode-only UI.
- See the root [README](../README.md#prerequisites) for exact versions and setup commands.

## Out of scope (this feature)

- Email verification and password reset.
- Cross-user notebook sharing/collaboration.
- The notebook workspace's "Features" column (reserved space only).
- Docker/Swarm deployment and containerization.

Full user-story-level acceptance criteria and clarification history live in
`specs/001-notebook-rag-chat/spec.md` (local spec-kit workspace, not
committed to the repo).
