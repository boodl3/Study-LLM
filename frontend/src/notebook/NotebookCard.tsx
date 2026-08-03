import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { GlassCard } from "../components/GlassCard";
import { IconButton } from "../components/IconButton";
import { useDeleteNotebook, useRenameNotebook, type Notebook } from "../api/notebooks";
import "./NotebookCard.css";

export function NotebookCard({ notebook }: { notebook: Notebook }) {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [title, setTitle] = useState(notebook.title);
  const renameNotebook = useRenameNotebook();
  const deleteNotebook = useDeleteNotebook();

  function handleOpen() {
    if (!menuOpen && !renaming) navigate(`/notebooks/${notebook.id}`);
  }

  async function submitRename() {
    const trimmed = title.trim();
    if (trimmed && trimmed !== notebook.title) {
      try {
        await renameNotebook.mutateAsync({ id: notebook.id, title: trimmed });
      } catch {
        setTitle(notebook.title);
      }
    }
    setRenaming(false);
  }

  function handleDelete() {
    setMenuOpen(false);
    if (window.confirm(`Delete "${notebook.title}"? This can't be undone.`)) {
      deleteNotebook.mutate(notebook.id);
    }
  }

  return (
    <GlassCard className="notebook-card" onClick={handleOpen}>
      {renaming ? (
        <input
          autoFocus
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onClick={(e) => e.stopPropagation()}
          onBlur={submitRename}
          onKeyDown={(e) => {
            if (e.key === "Enter") submitRename();
            if (e.key === "Escape") {
              setTitle(notebook.title);
              setRenaming(false);
            }
          }}
        />
      ) : (
        <div className="notebook-card__title">{notebook.title}</div>
      )}
      <div className="notebook-card__meta">
        {notebook.sourceCount} source{notebook.sourceCount === 1 ? "" : "s"}
      </div>
      {(renameNotebook.isError || deleteNotebook.isError) && (
        <div className="notebook-card__error">Something went wrong. Please try again.</div>
      )}

      <div className="notebook-card__menu-trigger">
        <IconButton
          label="Notebook options"
          onClick={(e) => {
            e.stopPropagation();
            setMenuOpen((open) => !open);
          }}
        >
          ⋮
        </IconButton>
      </div>

      {menuOpen && (
        <div className="notebook-card__menu" onClick={(e) => e.stopPropagation()}>
          <button
            onClick={() => {
              setMenuOpen(false);
              setRenaming(true);
            }}
          >
            Rename
          </button>
          <button className="danger" onClick={handleDelete}>
            Delete
          </button>
        </div>
      )}
    </GlassCard>
  );
}
