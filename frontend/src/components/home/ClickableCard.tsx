import type { CSSProperties, KeyboardEvent, MouseEvent, ReactNode } from "react";
import { Card } from "primereact/card";

// A Card that is itself the link — no inner button. Keyboard-reachable like a real link
// (Tab + Enter/Space), since a clickable <div> is otherwise invisible to keyboard users.
export interface ClickableCardProps {
  // The event is handed over for a card that opens a popup anchored to itself.
  onOpen: (event: MouseEvent<HTMLDivElement> | KeyboardEvent<HTMLDivElement>) => void;
  className?: string;
  style?: CSSProperties;
  ariaLabel?: string;
  children: ReactNode;
}

export function ClickableCard({ onOpen, className, style, ariaLabel, children }: ClickableCardProps) {
  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onOpen(e);
    }
  }

  return (
    <div
      role="link"
      tabIndex={0}
      aria-label={ariaLabel}
      className="sia-clickable-card"
      onClick={onOpen}
      onKeyDown={onKeyDown}
    >
      <Card className={className} style={style}>
        {children}
      </Card>
    </div>
  );
}
