import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { ApiError } from "../api/client";
import "../notebook/NewNotebookModal.css";

interface AddWebsiteModalProps {
  open: boolean;
  onCancel: () => void;
  onConfirm: (url: string) => void;
  submitting?: boolean;
  error?: unknown;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.body && typeof error.body === "object" && "message" in error.body) {
    return String((error.body as { message: unknown }).message);
  }
  return "Couldn't add that website. Check the URL and try again.";
}

function hasError(error: unknown): boolean {
  return error !== undefined && error !== null;
}

export function AddWebsiteModal({ open, onCancel, onConfirm, submitting, error }: AddWebsiteModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [url, setUrl] = useState("");

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      setUrl("");
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = url.trim();
    if (trimmed) onConfirm(trimmed);
  }

  function handleBackdropClick(e: MouseEvent<HTMLDialogElement>) {
    if (e.target === dialogRef.current) onCancel();
  }

  return (
    <dialog
      ref={dialogRef}
      className="new-notebook-modal"
      onCancel={onCancel}
      onClose={onCancel}
      onClick={handleBackdropClick}
    >
      <form onSubmit={handleSubmit} className="new-notebook-modal__fields">
        <h1>Add Website</h1>
        <input
          type="url"
          placeholder="https://example.com/article"
          autoFocus
          required
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        {hasError(error) && <p>{errorMessage(error)}</p>}
        <div className="new-notebook-modal__actions">
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" disabled={submitting}>
            {submitting ? "Adding…" : "Add"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
