import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from "react";
import "./NewNotebookModal.css";

interface NewNotebookModalProps {
  open: boolean;
  onCancel: () => void;
  onConfirm: (title: string) => void;
  submitting?: boolean;
}

export function NewNotebookModal({ open, onCancel, onConfirm, submitting }: NewNotebookModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [title, setTitle] = useState("");

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      setTitle("");
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    onConfirm(title.trim() || "Untitled notebook");
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
        <h1>New Notebook</h1>
        <input
          type="text"
          placeholder="Notebook name"
          autoFocus
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <div className="new-notebook-modal__actions">
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" disabled={submitting}>
            {submitting ? "Creating…" : "Confirm"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
