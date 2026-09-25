import { Chip } from "primereact/chip";
import type { PhaseDetail } from "./types";
import { getEntityType } from "../registry";

// phasePanelHeader.xhtml's content, reduced the same way FindDetailHeader's own is: identifier
// chip + a read-only type chip. The type field is already editable like any other field inside
// PhaseFicheTab's grid (it's part of Phase.DETAILS_FORM's layout), so a second, duplicated edit
// affordance in the header would just be two paths to the same write.
export interface PhaseDetailHeaderProps {
  entity: PhaseDetail;
  onSaved: () => void;
}

export function PhaseDetailHeader({ entity }: PhaseDetailHeaderProps) {
  return (
    <div className="phase-detail-header" style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}>
      <Chip label={entity.label || entity.identifier || ""} className="phase-chip-alt entity-nav-chip" icon={getEntityType("phase")?.icon} />
      {entity.type?.resolvedLabel && <Chip label={entity.type.resolvedLabel} className="mr-2 phase-type-chip" />}
    </div>
  );
}
