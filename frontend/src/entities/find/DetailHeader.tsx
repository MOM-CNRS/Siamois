import { Chip } from "primereact/chip";
import type { FindDetail } from "./types";

// specimenPanelHeader.xhtml's content, reduced: identifier chip + a read-only category chip.
// Unlike Project's/RecordingUnit's own DetailHeader, the category chip here is NOT inline-editable
// — the type field is already editable like any other field inside FindFicheTab's grid (it's part
// of Specimen.DETAILS_FORM's layout), so a second, duplicated edit affordance in the header would
// just be two paths to the same write. Revisit if the fiche ever hides the type field from its
// own grid the way Project/RecordingUnit hide their identifier/type fields there.
export interface FindDetailHeaderProps {
  entity: FindDetail;
  onSaved: () => void;
}

export function FindDetailHeader({ entity }: FindDetailHeaderProps) {
  return (
    <div className="find-detail-header" style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}>
      <Chip label={entity.fullIdentifier} className="entity-nav-chip" />
      {entity.type?.resolvedLabel && <Chip label={entity.type.resolvedLabel} className="recording-unit-chip-alt" />}
    </div>
  );
}
