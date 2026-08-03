import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useCreateNotebook, useNotebooks, useNotebookSearch } from "../api/notebooks";
import { NotebookCard } from "./NotebookCard";
import { NewNotebookModal } from "./NewNotebookModal";
import { SearchBar } from "./SearchBar";
import "./HomePage.css";

export function HomePage() {
  const [query, setQuery] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const { data: notebooks, isLoading } = useNotebooks();
  const { data: searchResults, isLoading: isSearching } = useNotebookSearch(query);
  const createNotebook = useCreateNotebook();
  const navigate = useNavigate();

  const handleSearch = useCallback((q: string) => setQuery(q), []);

  async function handleCreate(title: string) {
    try {
      const notebook = await createNotebook.mutateAsync(title);
      setIsModalOpen(false);
      navigate(`/notebooks/${notebook.id}`);
    } catch {
      // createNotebook.isError drives the inline message below.
    }
  }

  const isSearchActive = query.trim().length > 0;
  const visible = isSearchActive ? searchResults : notebooks;
  const loading = isSearchActive ? isSearching : isLoading;

  return (
    <div className="home-page">
      <div className="home-page__header">
        <h1>Your Notebooks</h1>
        <button className="home-page__new-button" onClick={() => setIsModalOpen(true)}>
          + New Notebook
        </button>
      </div>

      <SearchBar onSearch={handleSearch} />

      {createNotebook.isError && (
        <div className="home-page__empty">Couldn't create the notebook. Please try again.</div>
      )}

      {!loading && visible && visible.length === 0 && (
        <div className="home-page__empty">
          {isSearchActive
            ? "No notebooks match your search."
            : "You don't have any notebooks yet. Create one to get started."}
        </div>
      )}

      <div className="home-page__grid">
        {visible?.map((notebook) => <NotebookCard key={notebook.id} notebook={notebook} />)}
      </div>

      <NewNotebookModal
        open={isModalOpen}
        onCancel={() => setIsModalOpen(false)}
        onConfirm={handleCreate}
        submitting={createNotebook.isPending}
      />
    </div>
  );
}
