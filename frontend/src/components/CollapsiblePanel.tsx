import { useState, type ReactNode } from "react";
import { IconButton } from "./IconButton";
import "./CollapsiblePanel.css";

interface CollapsiblePanelProps {
  title: string;
  children: ReactNode;
  defaultCollapsed?: boolean;
  side?: "left" | "right";
}

export function CollapsiblePanel({
  title,
  children,
  defaultCollapsed = false,
  side = "left",
}: CollapsiblePanelProps) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  const sideClass = side === "left" ? "collapsible-panel--left" : "collapsible-panel--right";

  if (collapsed) {
    return (
      <div className={`collapsible-panel ${sideClass} collapsible-panel--collapsed`}>
        <IconButton label={`Expand ${title}`} onClick={() => setCollapsed(false)}>
          {side === "left" ? "›" : "‹"}
        </IconButton>
      </div>
    );
  }

  return (
    <div className={`collapsible-panel ${sideClass}`}>
      <div className="collapsible-panel__header">
        <span className="collapsible-panel__title">{title}</span>
        <IconButton label={`Collapse ${title}`} onClick={() => setCollapsed(true)}>
          {side === "left" ? "‹" : "›"}
        </IconButton>
      </div>
      <div className="collapsible-panel__body">{children}</div>
    </div>
  );
}
