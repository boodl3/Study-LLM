import { useParams } from "react-router-dom";
import { CollapsiblePanel } from "../components/CollapsiblePanel";
import { ChatConversation } from "../chat/ChatConversation";
import { ChatInput } from "../chat/ChatInput";
import { SourcesPanel } from "../sources/SourcesPanel";
import { useSources } from "../api/sources";
import { useStreamingChat } from "../api/chat";
import "./NotebookWorkspace.css";

export function NotebookWorkspace() {
  const { notebookId } = useParams<{ notebookId: string }>();
  const { data: sources } = useSources(notebookId ?? "");
  const chat = useStreamingChat(notebookId ?? "");
  if (!notebookId) return null;

  const hasReadySources = sources?.some((s) => s.status === "READY") ?? true;

  return (
    <div className="notebook-workspace">
      <CollapsiblePanel title="Sources" side="left">
        <SourcesPanel notebookId={notebookId} />
      </CollapsiblePanel>

      <div className="notebook-workspace__chat">
        <ChatConversation
          notebookId={notebookId}
          pendingQuestion={chat.pendingQuestion}
          streamingAnswer={chat.streamingAnswer}
        />
        <ChatInput
          hasReadySources={hasReadySources}
          isAsking={chat.isAsking}
          ask={chat.ask}
          error={chat.error}
        />
      </div>

      <CollapsiblePanel title="Features" side="right">
        <div className="notebook-workspace__features-empty">Coming soon.</div>
      </CollapsiblePanel>
    </div>
  );
}
