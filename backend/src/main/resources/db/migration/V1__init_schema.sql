CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notebooks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notebooks_owner_last_active ON notebooks (owner_id, last_active_at DESC);

CREATE TABLE sources (
    id UUID PRIMARY KEY,
    notebook_id UUID NOT NULL REFERENCES notebooks (id) ON DELETE CASCADE,
    filename VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL CHECK (file_type IN ('PDF', 'DOCX', 'PPTX', 'TXT', 'MD')),
    file_size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    failure_reason VARCHAR(1000),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at TIMESTAMPTZ
);

CREATE INDEX idx_sources_notebook ON sources (notebook_id);

CREATE TABLE chunks (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    position INT NOT NULL,
    section_label VARCHAR(200),
    embedding vector(768) NOT NULL
);

CREATE INDEX idx_chunks_source ON chunks (source_id);
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    notebook_id UUID NOT NULL REFERENCES notebooks (id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    cited_chunk_ids UUID[],
    not_found_in_sources BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_notebook_created ON chat_messages (notebook_id, created_at);
