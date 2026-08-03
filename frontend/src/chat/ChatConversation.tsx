import { useEffect, useRef } from "react";
import { useChatHistory, type ChatMessage } from "../api/chat";
import "./ChatConversation.css";

// Splits on **bold** markers and renders the bracketed pieces as <strong>; everything else
// passes through as plain text (the model only ever emphasizes with this one markdown construct).
function renderFormattedContent(content: string) {
  return content
    .split(/(\*\*[^*]+\*\*)/g)
    .map((part, i) =>
      part.startsWith("**") && part.endsWith("**") ? <strong key={i}>{part.slice(2, -2)}</strong> : part,
    );
}

function ChatMessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === "USER";
  return (
    <div
      className={[
        "chat-message",
        isUser ? "chat-message--user" : "chat-message--assistant",
        message.notFoundInSources ? "chat-message--not-found" : "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <div>{renderFormattedContent(message.content)}</div>
      {message.citedSources.length > 0 && (
        <div className="chat-message__citations">
          {message.citedSources.map((source, i) => (
            <span className="chat-message__citation" key={`${source.sourceId}-${i}`}>
              {source.filename}
              {source.sectionLabel ? `, ${source.sectionLabel}` : ""}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export function ChatConversation({ notebookId }: { notebookId: string }) {
  const { data: messages, isLoading } = useChatHistory(notebookId);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  return (
    <div className="chat-conversation">
      <div className="chat-conversation__messages">
        {isLoading && <div className="chat-conversation__empty">Loading…</div>}
        {!isLoading && (!messages || messages.length === 0) && (
          <div className="chat-conversation__empty">
            Ask a question about this notebook's sources to get started.
          </div>
        )}
        {messages?.map((message) => (
          <ChatMessageBubble key={message.id} message={message} />
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
