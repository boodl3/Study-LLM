import { useEffect, useRef } from "react";
import katex from "katex";
import "katex/dist/katex.min.css";
import { useChatHistory, type ChatMessage } from "../api/chat";
import "./ChatConversation.css";

function renderMath(tex: string, displayMode: boolean) {
  try {
    return katex.renderToString(tex, { throwOnError: false, displayMode });
  } catch {
    return tex;
  }
}

// Splits on **bold** and $...$/$$...$$ math markers (the model's two formatting constructs)
// and renders each; everything else passes through as plain text.
export function renderFormattedContent(content: string) {
  return content
    .split(/(\*\*[^*]+\*\*|\$\$[^$]+\$\$|\$[^$]+\$)/g)
    .map((part, i) => {
      if (part.startsWith("**") && part.endsWith("**")) {
        return <strong key={i}>{part.slice(2, -2)}</strong>;
      }
      if (part.startsWith("$$") && part.endsWith("$$")) {
        return <span key={i} dangerouslySetInnerHTML={{ __html: renderMath(part.slice(2, -2), true) }} />;
      }
      if (part.startsWith("$") && part.endsWith("$")) {
        return <span key={i} dangerouslySetInnerHTML={{ __html: renderMath(part.slice(1, -1), false) }} />;
      }
      return part;
    });
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

interface ChatConversationProps {
  notebookId: string;
  /** The question currently being answered, or null when nothing is in flight. */
  pendingQuestion: string | null;
  /** The answer generated so far for `pendingQuestion`, rendered as it streams in. */
  streamingAnswer: string;
}

export function ChatConversation({
  notebookId,
  pendingQuestion,
  streamingAnswer,
}: ChatConversationProps) {
  const { data: messages, isLoading } = useChatHistory(notebookId);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamingAnswer]);

  const isEmpty = !isLoading && (!messages || messages.length === 0) && !pendingQuestion;

  return (
    <div className="chat-conversation">
      <div className="chat-conversation__messages">
        {isLoading && <div className="chat-conversation__empty">Loading…</div>}
        {isEmpty && (
          <div className="chat-conversation__empty">
            Ask a question about this notebook's sources to get started.
          </div>
        )}
        {messages?.map((message) => (
          <ChatMessageBubble key={message.id} message={message} />
        ))}
        {pendingQuestion && (
          <>
            <div className="chat-message chat-message--user">
              <div>{pendingQuestion}</div>
            </div>
            <div className="chat-message chat-message--assistant">
              <div>
                {streamingAnswer ? (
                  renderFormattedContent(streamingAnswer)
                ) : (
                  <span className="chat-thinking">
                    <span className="chat-thinking__dot" />
                    <span className="chat-thinking__dot" />
                    <span className="chat-thinking__dot" />
                  </span>
                )}
              </div>
            </div>
          </>
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
