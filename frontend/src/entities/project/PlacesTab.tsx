import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Chip } from "primereact/chip";
import type { DetailTabHelpers } from "../types";
import type { PlaceLight, ProjectDetail } from "./types";

// Migration plan Lot 4 ("Lieux") decision: unlike the other relation tabs on this fiche
// (recording-units/containers/phases/finds, all built with panels/relationTab.tsx's generic
// EntityListPanel wrapper), Place has no scoped list endpoint at all — there is no
// GET /api/v1/projects/{id}/places. The project's own detail response already carries every
// place it needs (`spatialContext`, plus `mainLocation` flagged as the main one), so this tab is
// a plain, static table over that array instead — no pagination, no sort, no per-column filter,
// no dynamic columns (SpatialUnit isn't in ConfigurableTable, so there is no catalog to drive
// them from either). Rows navigate to the Place fiche via onOpenOverview("place", id), the same
// entry point every other row-click in this app uses.
interface PlacesTabProps {
  entity: ProjectDetail;
  helpers: DetailTabHelpers;
}

interface PlaceRow extends PlaceLight {
  isMain: boolean;
}

export function PlacesTab({ entity, helpers }: PlacesTabProps) {
  const mainId = entity.mainLocation?.id;
  const rows: PlaceRow[] = (entity.spatialContext ?? []).map((place) => ({
    ...place,
    isMain: place.id === mainId,
  }));
  // mainLocation may not be part of spatialContext (JSF keeps them as two separate concepts) —
  // add it if the array didn't already carry it, so the "principale" badge always has a row.
  if (mainId != null && !rows.some((row) => row.id === mainId) && entity.mainLocation) {
    rows.unshift({ ...entity.mainLocation, isMain: true });
  }

  function openPlace(id: string | number) {
    if (helpers.onOpenOverview) helpers.onOpenOverview("place", id);
    else helpers.onNavigate?.("place", id);
  }

  return (
    <div className="places-tab sia-fiche-tab">
      <DataTable value={rows} dataKey="id" emptyMessage="Aucun lieu">
        <Column
          header="Nom"
          body={(row: PlaceRow) => (
            <span
              className="entity-list-panel-identifier-link entity-nav-chip"
              role="button"
              tabIndex={0}
              onClick={() => openPlace(row.id)}
            >
              <i className="bi bi-geo-alt" aria-hidden="true" />
              <span className="entity-nav-chip-label">{row.name ?? ""}</span>
            </span>
          )}
        />
        <Column
          header="Rôle"
          body={(row: PlaceRow) => (row.isMain ? <Chip label="Localisation principale" /> : null)}
        />
      </DataTable>
    </div>
  );
}
