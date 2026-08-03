import type { HTMLAttributes, ReactNode } from "react";
import "./GlassCard.css";

interface GlassCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

export function GlassCard({ children, className, ...rest }: GlassCardProps) {
  return (
    <div className={["glass-card", className].filter(Boolean).join(" ")} {...rest}>
      {children}
    </div>
  );
}
