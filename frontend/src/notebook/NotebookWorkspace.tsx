import { useParams } from "react-router-dom";
import { CollapsiblePanel } from "../components/CollapsiblePanel";
import { ChatConversation } from "../chat/ChatConversation";
import { ChatInput } from "../chat/ChatInput";
import { SourcesPanel } from "../sources/SourcesPanel";
import { useSources } from "../api/sources";
import "./NotebookWorkspace.css";

export function NotebookWorkspace() {
  const { notebookId } = useParams<{ notebookId: string }>();
  const { data: sources } = useSources(notebookId ?? "");
  if (!notebookId) return null;

  const hasReadySources = sources?.some((s) => s.status === "READY") ?? true;

  return (
    <div className="notebook-workspace">
      <div className="notebook-workspace__sources">
        <CollapsiblePanel title="Sources" side="left">
          <SourcesPanel notebookId={notebookId} />
        </CollapsiblePanel>
      </div>

      <div className="notebook-workspace__chat">
        <ChatConversation notebookId={notebookId} />
        <ChatInput notebookId={notebookId} hasReadySources={hasReadySources} />
      </div>

      <div className="notebook-workspace__features">
        <CollapsiblePanel title="Features" side="right">
          <div className="notebook-workspace__features-empty">Coming soon.</div>
        </CollapsiblePanel>
      </div>
    </div>
  );
}
