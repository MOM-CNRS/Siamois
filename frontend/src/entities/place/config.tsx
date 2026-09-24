import type { EntityRef, EntityTypeConfig } from "../types";
import { relationTab } from "../../panels/relationTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { duplicatePlace, getPlace, listPlaces, patchPlaceAnswers } from "./api";
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
function placeRef(place: PlaceSummary): EntityRef {
  return { id: place.id, label: place.name ?? String(place.id) };
}

export const placeEntityConfig: EntityTypeConfig<PlaceSummary, PlaceDetail> = {
  key: "place",
  labels: { singular: "Lieu", plural: "Lieux" },
  collectionPath: "places",
  // Matches SpatialUnitPanel's own icon ("bi bi-geo-alt").
  icon: "bi bi-geo-alt",
  api: {
    siblings: (id) => fetchSiblings("places", id),
    get: getPlace,
    list: listPlaces,
    patchAnswers: (id, answers) => patchPlaceAnswers(id, answers),
    duplicate: duplicatePlace,
  },
  list: {
    columns: placeColumns,
    defaultSort: "name:asc",
    searchable: true,
    // Places belong to the organization, not a project: the one organization-wide list whose
    // creation is allowed (see CreateForm.tsx).
    createForm: (ctx) => <PlaceCreateForm {...ctx} />,
    // JSF's SpatialUnitTableViewModel row actions, after the generic bookmark/duplicate.
    rowActions: [
      {
        key: "new-parent",
        icon: "bi bi-node-plus-fill rotate-minus90",
        tooltip: "Créer un lieu parent",
        run: (row, ctx) => ctx.openCreate("place", { prefill: { child: placeRef(row) } }),
      },
      {
        key: "new-child",
        icon: "bi bi-node-plus-fill rotate-90",
        tooltip: "Créer un lieu enfant",
        run: (row, ctx) => ctx.openCreate("place", { prefill: { parent: placeRef(row) } }),
      },
      {
        key: "new-project",
        icon: "bi bi-arrow-down-square",
        tooltip: "Créer un projet",
        run: (row, ctx) => ctx.openCreate("project", { prefill: { spatialContext: placeRef(row) } }),
      },
    ],
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity, helpers) => <PlaceFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
      // JSF's hierarchy tab, reduced to the places this one contains.
      relationTab<PlaceDetail>({
        key: "children",
        label: "Lieux",
        target: "place",
        scopeEntityType: "place",
        path: "children",
        createPrefill: (entity) => ({ parent: placeRef(entity) }),
        badge: (entity) => entity._counts?.children ?? 0,
      }),
      // JSF's ActionTab: the projects whose spatial context contains this place.
      relationTab<PlaceDetail>({
        key: "projects",
        label: "Projets",
        target: "project",
        scopeEntityType: "place",
        path: "projects",
        createPrefill: (entity) => ({ spatialContext: placeRef(entity) }),
        badge: (entity) => entity._counts?.projects ?? 0,
      }),
    ],
    header: (entity, helpers) => <PlaceDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.name),
  },
  routes: PLACE_ROUTES,
  home: {
    widgets: placeHomeWidgets,
  },
};
