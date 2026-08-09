import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { IconButton } from "../components/IconButton";
import { ConfirmDialog } from "../notebook/ConfirmDialog";
import {
  useDeleteSource,
  useRenameSource,
  useReorderSources,
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

function FolderGroup({
  folderName,
  sources,
  notebookId,
}: {
  folderName: string;
  sources: Source[];
  notebookId: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="source-folder">
      <button className="source-folder__trigger" onClick={() => setOpen((o) => !o)}>
        <span className={`source-folder__chevron${open ? " source-folder__chevron--open" : ""}`}>
          ›
        </span>
        <span className="source-folder__name">{folderName}</span>
        <span className="source-folder__count">
          {sources.length} file{sources.length === 1 ? "" : "s"}
        </span>
      </button>
      {open && (
        <div className="source-folder__files">
          {sources.map((source) => (
            <SourceItem key={source.id} source={source} notebookId={notebookId} />
          ))}
        </div>
      )}
    </div>
  );
}

const SUPPORTED_EXTENSIONS = [".pdf", ".docx", ".pptx", ".txt", ".md"];

function isSupported(filename: string) {
  return SUPPORTED_EXTENSIONS.some((ext) => filename.toLowerCase().endsWith(ext));
}

type GroupedItem =
  | { kind: "file"; key: string; source: Source }
  | { kind: "folder"; key: string; folderName: string; sources: Source[] };

// Sources sharing a folderName collapse into one group, positioned at the first member's spot.
function groupSources(sources: Source[]): GroupedItem[] {
  const items: GroupedItem[] = [];
  const seenFolders = new Set<string>();
  for (const source of sources) {
    if (source.folderName) {
      if (seenFolders.has(source.folderName)) continue;
      seenFolders.add(source.folderName);
      items.push({
        kind: "folder",
        key: `folder:${source.folderName}`,
        folderName: source.folderName,
        sources: sources.filter((s) => s.folderName === source.folderName),
      });
    } else {
      items.push({ kind: "file", key: source.id, source });
    }
  }
  return items;
}

function flattenOrder(items: GroupedItem[]): string[] {
  return items.flatMap((item) =>
    item.kind === "file" ? [item.source.id] : item.sources.map((s) => s.id),
  );
}

function moveItem<T>(items: T[], fromIndex: number, toIndex: number): T[] {
  const next = items.slice();
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  return next;
}

function viewTransitionNameFor(key: string) {
  return `source-row-${key.replace(/[^a-zA-Z0-9_-]/g, "_")}`;
}

// Animates the DOM mutation performed by `update` via the View Transitions API when the
// browser supports it (Chromium today); other browsers just apply the reorder instantly.
function withReorderAnimation(update: () => void) {
  const doc = document as Document & { startViewTransition?: (cb: () => void) => unknown };
  if (doc.startViewTransition) {
    doc.startViewTransition(() => flushSync(update));
  } else {
    update();
  }
}

export function SourcesPanel({ notebookId }: { notebookId: string }) {
  const { data: sources, isLoading } = useSources(notebookId);
  const uploadSource = useUploadSource(notebookId);
  const reorderSources = useReorderSources(notebookId);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [importingFolder, setImportingFolder] = useState(false);
  const [folderError, setFolderError] = useState(false);
  const [dragKey, setDragKey] = useState<string | null>(null);
  const [orderOverride, setOrderOverride] = useState<string[] | null>(null);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) uploadSource.mutate({ file });
    e.target.value = "";
  }

  async function handleFolderChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).filter((f) => isSupported(f.name));
    e.target.value = "";
    if (files.length === 0) return;

    setFolderError(false);
    setImportingFolder(true);
    try {
      for (const file of files) {
        const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath;
        const folderName = relativePath?.split("/")[0];
        await uploadSource.mutateAsync({ file, folderName });
      }
    } catch {
      setFolderError(true);
    } finally {
      setImportingFolder(false);
    }
  }

  const busy = uploadSource.isPending || importingFolder;
  const items = groupSources(sources ?? []);
  const itemsByKey = new Map(items.map((item) => [item.key, item]));
  // While dragging, the list is displayed in the live-preview order; otherwise it just
  // reflects the server's order. orderOverride can go stale (e.g. a source gets deleted
  // mid-drag) — filter out keys that no longer exist rather than crash on the lookup.
  const displayItems = orderOverride
    ? orderOverride
        .map((key) => itemsByKey.get(key))
        .filter((item): item is GroupedItem => item !== undefined)
    : items;

  function startDrag(key: string) {
    setDragKey(key);
    setOrderOverride(items.map((item) => item.key));
  }

  // Recomputes on every dragover rather than deduping by "last row entered": since a reorder
  // shuffles rows under a stationary cursor, the same row can legitimately need reprocessing
  // (e.g. dragging back over a row you've already passed, to undo that swap). The `dragKey ===
  // targetKey` check below is what stops the churn — once the dragged item settles under the
  // cursor there's nothing left to compute, so this naturally reaches a fixed point.
  function previewDragOver(e: React.DragEvent, targetKey: string) {
    e.preventDefault();
    if (!dragKey || dragKey === targetKey) return;

    const currentOrder = orderOverride ?? items.map((item) => item.key);
    const fromIndex = currentOrder.indexOf(dragKey);
    const toIndex = currentOrder.indexOf(targetKey);
    if (fromIndex === -1 || toIndex === -1) return;
    withReorderAnimation(() => setOrderOverride(moveItem(currentOrder, fromIndex, toIndex)));
  }

  function commitDrop(e: React.DragEvent) {
    e.preventDefault();
    // Only persist if the preview actually differs from the server's order — e.g. dragging a
    // source back to where it started should cancel, not round-trip a no-op PATCH.
    if (orderOverride && flattenOrder(displayItems).join() !== flattenOrder(items).join()) {
      reorderSources.mutate(flattenOrder(displayItems));
    }
    endDrag();
  }

  function endDrag() {
    setDragKey(null);
    setOrderOverride(null);
  }

  return (
    <div className="sources-panel">
      <div className="sources-panel__add-row">
        <label className="sources-panel__add">
          {uploadSource.isPending && !importingFolder ? "Uploading…" : "+ Add source"}
          <input
            ref={fileInputRef}
            type="file"
            accept={SUPPORTED_EXTENSIONS.join(",")}
            onChange={handleFileChange}
            disabled={busy}
          />
        </label>
        <label className="sources-panel__add">
          {importingFolder ? "Importing…" : "+ Add folder"}
          <input
            type="file"
            /* @ts-expect-error non-standard attributes for directory selection */
            webkitdirectory=""
            directory=""
            multiple
            onChange={handleFolderChange}
            disabled={busy}
          />
        </label>
      </div>

      {isLoading && <div className="sources-panel__empty">Loading…</div>}
      {!isLoading && sources && sources.length === 0 && (
        <div className="sources-panel__empty">No sources yet.</div>
      )}
      {(uploadSource.isError || folderError) && (
        <div className="source-item__reason">Upload failed. Please try again.</div>
      )}

      {displayItems.map((item) => (
        <div
          key={item.key}
          draggable
          onDragStart={() => startDrag(item.key)}
          onDragOver={(e) => previewDragOver(e, item.key)}
          onDrop={commitDrop}
          onDragEnd={endDrag}
          style={{ viewTransitionName: viewTransitionNameFor(item.key) }}
          className={`sources-panel__drag-row${dragKey === item.key ? " sources-panel__drag-row--dragging" : ""}`}
        >
          {item.kind === "file" ? (
            <SourceItem source={item.source} notebookId={notebookId} />
          ) : (
            <FolderGroup folderName={item.folderName} sources={item.sources} notebookId={notebookId} />
          )}
        </div>
      ))}
    </div>
  );
}
