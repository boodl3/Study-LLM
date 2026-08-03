import { useState, type FormEvent } from "react";
import { useAskQuestion } from "../api/chat";
import { ApiError } from "../api/client";
import "./ChatInput.css";

interface ChatInputProps {
  notebookId: string;
  /** Whether the notebook has at least one READY source (spec FR-018). Defaults to enabled so
   * this component works standalone against seeded data before the Sources panel exists. */
  hasReadySources?: boolean;
}

export function ChatInput({ notebookId, hasReadySources = true }: ChatInputProps) {
  const [question, setQuestion] = useState("");
  const [blockedMessage, setBlockedMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const askQuestion = useAskQuestion(notebookId);

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
    setBlockedMessage(null);
    setErrorMessage(null);
    try {
      await askQuestion.mutateAsync(trimmed);
      setQuestion("");
    } catch (err) {
      if (err instanceof ApiError && err.status === 422) {
        setBlockedMessage("Upload a source before asking a question.");
      } else {
        setErrorMessage("Couldn't get an answer. Please try again.");
      }
    }
  }

  if (blockedMessage) {
    return <div className="chat-input__prompt">{blockedMessage}</div>;
  }

  return (
    <div>
      {errorMessage && <div className="chat-input__error">{errorMessage}</div>}
      <form className="chat-input" onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="Ask a question about this notebook…"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          disabled={askQuestion.isPending}
        />
        <button type="submit" disabled={askQuestion.isPending || !question.trim()}>
          {askQuestion.isPending ? "Asking…" : "Ask"}
        </button>
      </form>
    </div>
  );
}
