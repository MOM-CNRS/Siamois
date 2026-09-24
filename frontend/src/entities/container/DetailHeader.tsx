import { Chip } from "primereact/chip";
import type { ContainerDetail } from "./types";

// containerPanelHeader.xhtml's content, reduced the same way Phase/FindDetailHeader's own is:
// identifier chip + a read-only type chip. The type field is already editable like any other
// field inside ContainerFicheTab's grid.
export interface ContainerDetailHeaderProps {
  entity: ContainerDetail;
  onSaved: () => void;
}

export function ContainerDetailHeader({ entity }: ContainerDetailHeaderProps) {
  return (
    <div className="container-detail-header" style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}>
      <Chip label={entity.identifier || ""} className="entity-nav-chip" />
      {entity.type?.resolvedLabel && <Chip label={entity.type.resolvedLabel} className="recording-unit-chip-alt" />}
    </div>
  );
}
