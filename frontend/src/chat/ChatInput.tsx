import { useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import "./ChatInput.css";

interface ChatInputProps {
  /** Whether the notebook has at least one READY source (spec FR-018). Defaults to enabled so
   * this component works standalone against seeded data before the Sources panel exists. */
  hasReadySources?: boolean;
  isAsking: boolean;
  ask: (question: string) => Promise<void>;
  error: Error | null;
}

export function ChatInput({ hasReadySources = true, isAsking, ask, error }: ChatInputProps) {
  const [question, setQuestion] = useState("");

  if (!hasReadySources) {
    return (
      <div className="chat-input__prompt">
        Upload a source to start asking questions about this notebook.
      </div>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = question.trim();
    if (!trimmed) return;
    setQuestion("");
    await ask(trimmed);
  }

  const isBlocked = error instanceof ApiError && error.status === 422;
  if (isBlocked) {
    return <div className="chat-input__prompt">Upload a source before asking a question.</div>;
  }

  return (
    <div>
      {error && <div className="chat-input__error">Couldn't get an answer. Please try again.</div>}
      <form className="chat-input" onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="Ask a question about this notebook…"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          disabled={isAsking}
        />
        <button type="submit" disabled={isAsking || !question.trim()}>
          {isAsking ? "Asking…" : "Ask"}
        </button>
      </form>
    </div>
  );
}
