import { Chip } from "primereact/chip";
import { ClickableCard } from "./ClickableCard";

// React counterpart of pages/shared/card/welcomeCard.xhtml, minus its footer button: the whole
// card opens the list.
export interface EntityCountCardProps {
  icon: string;
  label: string;
  description: string;
  // Undefined while the counts are loading.
  count?: number;
  // JSF's own classes (sia-welcome-card sia-<type>) and chip class, kept for the theme.
  className: string;
  chipClassName: string;
  onOpen: () => void;
}

export function EntityCountCard({ icon, label, description, count, className, chipClassName, onOpen }: EntityCountCardProps) {
  return (
    <ClickableCard onOpen={onOpen} className={className} ariaLabel={label}>
      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
        <i className={icon} aria-hidden="true" />
        <span>{label}</span>
        <Chip label={count == null ? "…" : String(count)} className={chipClassName} />
      </div>
      <div>
        <small>{description}</small>
      </div>
    </ClickableCard>
  );
}
