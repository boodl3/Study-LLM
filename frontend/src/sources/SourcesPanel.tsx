import { useRef } from "react";
import { useSources, useUploadSource, type Source } from "../api/sources";
import "./SourcesPanel.css";

const STATUS_LABEL: Record<Source["status"], string> = {
  READY: "Ready",
  PROCESSING: "Processing…",
  FAILED: "Failed",
};

function SourceItem({ source }: { source: Source }) {
  return (
    <div className="source-item">
      <div className="source-item__row">
        <span className="source-item__name" title={source.filename}>
          {source.filename}
        </span>
        <span className={`source-item__status source-item__status--${source.status.toLowerCase()}`}>
          {STATUS_LABEL[source.status]}
        </span>
      </div>
      {source.status === "FAILED" && source.failureReason && (
        <span className="source-item__reason">{source.failureReason}</span>
      )}
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
        <SourceItem key={source.id} source={source} />
      ))}
    </div>
  );
}
