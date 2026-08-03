import { useRef, useState } from "react";
import { IconButton } from "../components/IconButton";
import { ConfirmDialog } from "../notebook/ConfirmDialog";
import {
  useDeleteSource,
  useRenameSource,
  useSources,
  useUploadSource,
  type Source,
} from "../api/sources";
import "./SourcesPanel.css";

const STATUS_LABEL: Record<Source["status"], string> = {
  READY: "Ready",
  PROCESSING: "Processing…",
  FAILED: "Failed",
};

function SourceItem({ source, notebookId }: { source: Source; notebookId: string }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [filename, setFilename] = useState(source.filename);
  const renameSource = useRenameSource(notebookId);
  const deleteSource = useDeleteSource(notebookId);

  async function submitRename() {
    const trimmed = filename.trim();
    if (trimmed && trimmed !== source.filename) {
      try {
        await renameSource.mutateAsync({ id: source.id, filename: trimmed });
      } catch {
        setFilename(source.filename);
      }
    }
    setRenaming(false);
  }

  function confirmDelete() {
    deleteSource.mutate(source.id, { onSuccess: () => setConfirmingDelete(false) });
  }

  return (
    <div className="source-item">
      <div className="source-item__row">
        {renaming ? (
          <input
            className="source-item__name-input"
            autoFocus
            value={filename}
            onChange={(e) => setFilename(e.target.value)}
            onBlur={submitRename}
            onKeyDown={(e) => {
              if (e.key === "Enter") submitRename();
              if (e.key === "Escape") {
                setFilename(source.filename);
                setRenaming(false);
              }
            }}
          />
        ) : (
          <span className="source-item__name" title={source.filename}>
            {source.filename}
          </span>
        )}
        <span className={`source-item__status source-item__status--${source.status.toLowerCase()}`}>
          {STATUS_LABEL[source.status]}
        </span>

        <div className="source-item__menu-wrap">
          <IconButton
            label="Source options"
            className="source-item__menu-trigger"
            onClick={() => setMenuOpen((open) => !open)}
          >
            ⋮
          </IconButton>

          {menuOpen && (
            <div className="source-item__menu">
              <button
                onClick={() => {
                  setMenuOpen(false);
                  setRenaming(true);
                }}
              >
                Rename
              </button>
              <button
                className="danger"
                onClick={() => {
                  setMenuOpen(false);
                  setConfirmingDelete(true);
                }}
              >
                Delete
              </button>
            </div>
          )}
        </div>
      </div>
      {(renameSource.isError || deleteSource.isError) && (
        <span className="source-item__reason">Something went wrong. Please try again.</span>
      )}
      {source.status === "FAILED" && source.failureReason && (
        <span className="source-item__reason">{source.failureReason}</span>
      )}

      <ConfirmDialog
        open={confirmingDelete}
        title="Delete source"
        message={`Delete "${source.filename}"? This can't be undone.`}
        confirmLabel="Delete"
        submitting={deleteSource.isPending}
        onCancel={() => setConfirmingDelete(false)}
        onConfirm={confirmDelete}
      />
    </div>
  );
}

export function SourcesPanel({ notebookId }: { notebookId: string }) {
  const { data: sources, isLoading } = useSources(notebookId);
  const uploadSource = useUploadSource(notebookId);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) uploadSource.mutate(file);
    e.target.value = "";
  }

  return (
    <div className="sources-panel">
      <label className="sources-panel__add">
        {uploadSource.isPending ? "Uploading…" : "+ Add source"}
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx,.pptx,.txt,.md"
          onChange={handleFileChange}
          disabled={uploadSource.isPending}
        />
      </label>

      {isLoading && <div className="sources-panel__empty">Loading…</div>}
      {!isLoading && sources && sources.length === 0 && (
        <div className="sources-panel__empty">No sources yet.</div>
      )}
      {uploadSource.isError && (
        <div className="source-item__reason">Upload failed. Please try again.</div>
      )}

      {sources?.map((source) => (
        <SourceItem key={source.id} source={source} notebookId={notebookId} />
      ))}
    </div>
  );
}
