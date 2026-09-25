import { Chip } from "primereact/chip";
import type { PlaceDetail } from "./types";
import { getEntityType } from "../registry";

// spatialUnitPanelHeader.xhtml's content, reduced the same way FindDetailHeader's/
// PhaseDetailHeader's own is: name chip + a read-only type chip. The type field is already
// editable like any other field inside PlaceFicheTab's grid (it's part of
// SpatialUnit.DETAILS_FORM's layout), so a second, duplicated edit affordance in the header would
// just be two paths to the same write.
export interface PlaceDetailHeaderProps {
  entity: PlaceDetail;
  onSaved: () => void;
}

export function PlaceDetailHeader({ entity }: PlaceDetailHeaderProps) {
  return (
    <div className="place-detail-header" style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}>
      <Chip label={entity.name || ""} className="spatial-unit-chip-alt entity-nav-chip" icon={getEntityType("place")?.icon} />
      {entity.type?.resolvedLabel && <Chip label={entity.type.resolvedLabel} className="mr-2 spatial-unit-type-chip" />}
    </div>
  );
}
