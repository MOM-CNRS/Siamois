import type { EntityTypeConfig } from "../types";
import { getPlace, listPlaces, patchPlaceAnswers } from "./api";
import { placeColumns } from "./columns";
import { PlaceCreateForm } from "./CreateForm";
import { PlaceDetailHeader } from "./DetailHeader";
import { PlaceFicheTab } from "./FicheTab";
import { PLACE_ROUTES } from "./routes";
import { placeHomeWidgets } from "./homeWidgets";
import type { PlaceDetail, PlaceSummary } from "./types";

// Place's own EntityTypeConfig (migration plan, lot 4 "Lieux", then the organization-wide list
// lot). Two list contexts: the organization-wide list (GET /api/v1/places?organizationId=…, JSF's
// SpatialUnitListPanel) uses `list` below; the project fiche's "Lieux" tab does NOT — it is a
// static table over ProjectDetail.spatialContext (entities/project/PlacesTab.tsx).
export const placeEntityConfig: EntityTypeConfig<PlaceSummary, PlaceDetail> = {
  key: "place",
  labels: { singular: "Lieu", plural: "Lieux" },
  collectionPath: "places",
  // Matches SpatialUnitPanel's own icon ("bi bi-geo-alt").
  icon: "bi bi-geo-alt",
  api: {
    get: getPlace,
    list: listPlaces,
    patchAnswers: (id, answers) => patchPlaceAnswers(id, answers),
  },
  list: {
    columns: placeColumns,
    defaultSort: "name:asc",
    searchable: true,
    // Places belong to the organization, not a project: the one organization-wide list whose
    // creation is allowed (see CreateForm.tsx).
    createForm: (ctx) => <PlaceCreateForm {...ctx} />,
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity, helpers) => <PlaceFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
    ],
    header: (entity, helpers) => <PlaceDetailHeader entity={entity} onSaved={helpers.refetch} />,
  },
  routes: PLACE_ROUTES,
  home: {
    widgets: placeHomeWidgets,
  },
};
