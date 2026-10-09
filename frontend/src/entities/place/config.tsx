import type { EntityRef, EntityTypeConfig } from "../types";
import { relationTab } from "../../panels/relationTab";
import { documentsTab } from "../document/documentsTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { duplicatePlace, getPlace, listPlaces, patchPlaceAnswers } from "./api";
import { loadPlaceCatalog } from "./catalog";
import { placeColumns } from "./columns";
import { PlaceCreateForm } from "./CreateForm";
import { PlaceDetailHeader } from "./DetailHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import type { PlaceDetail, PlaceSummary } from "./types";
import { t } from "../../i18n";

// Place's own EntityTypeConfig (migration plan, lot 4 "Lieux", then the organization-wide list
// lot). Two list contexts: the organization-wide list (GET /api/v1/places?organizationId=…, JSF's
// SpatialUnitListPanel) uses `list` below; the project fiche's "Lieux" tab does NOT — it is a
// static table over ProjectDetail.spatialContext (entities/project/PlacesTab.tsx).
function placeRef(place: PlaceSummary): EntityRef {
  return { id: place.id, label: place.name ?? String(place.id) };
}

export const placeEntityConfig: EntityTypeConfig<PlaceSummary, PlaceDetail> = {
  key: "place",
  labels: { singular: t("entity.place.singular"), plural: t("entity.place.plural") },
  collectionPath: "places",
  // Matches SpatialUnitPanel's own icon ("bi bi-geo-alt").
  icon: "bi bi-geo-alt",
  panelClass: "spatial-unit-panel",
  api: {
    siblings: (id) => fetchSiblings("places", id),
    get: getPlace,
    list: listPlaces,
    patchAnswers: (id, answers) => patchPlaceAnswers(id, answers),
    duplicate: duplicatePlace,
  },
  list: {
    // Dynamic columns: the place form's fields, picked and sorted/filtered like the other lists'.
    schema: { load: loadPlaceCatalog },
    columns: placeColumns,
    // Every list: newest first — the order the fiche's prev/next arrows walk (↓ = the row below).
    defaultSort: "creationTime:desc",
    searchable: true,
    // Places belong to the organization, not a project: the one organization-wide list whose
    // creation is allowed (see CreateForm.tsx).
    createForm: (ctx) => <PlaceCreateForm {...ctx} />,
    // JSF's SpatialUnitTableViewModel row actions, after the generic bookmark/duplicate.
    rowActions: [
      {
        key: "new-parent",
        icon: "bi bi-node-plus-fill rotate-minus90",
        tooltip: t("row.newParentPlace"),
        run: (row, ctx) => ctx.openCreate("place", { prefill: { child: placeRef(row) } }),
      },
      {
        key: "new-child",
        icon: "bi bi-node-plus-fill rotate-90",
        tooltip: t("row.newChildPlace"),
        run: (row, ctx) => ctx.openCreate("place", { prefill: { parent: placeRef(row) } }),
      },
      {
        key: "new-project",
        icon: "bi bi-arrow-down-square",
        tooltip: t("row.newProject"),
        run: (row, ctx) => ctx.openCreate("project", { prefill: { spatialContext: placeRef(row) } }),
      },
    ],
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="place" isFieldShown={(field) => field.valueBinding !== "address"} save={patchPlaceAnswers} onSaved={helpers.refetch} />
        ),
      },
      // JSF's hierarchy tab, reduced to the places this one contains.
      relationTab<PlaceDetail>({
        key: "children",
        label: t("entity.place.plural"),
        target: "place",
        scopeEntityType: "place",
        path: "children",
        createPrefill: (entity) => ({ parent: placeRef(entity) }),
        badge: (entity) => entity._counts?.children ?? 0,
      }),
      // JSF's ActionTab: the projects whose spatial context contains this place.
      relationTab<PlaceDetail>({
        key: "projects",
        label: t("entity.project.plural"),
        target: "project",
        scopeEntityType: "place",
        path: "projects",
        createPrefill: (entity) => ({ spatialContext: placeRef(entity) }),
        badge: (entity) => entity._counts?.projects ?? 0,
      }),
      // A place has no project: the documents of the organization that name it.
      documentsTab<PlaceDetail>({
        scopeEntityType: "place",
        segment: "places",
        linkField: "placeIds",
        badge: (entity) => entity._counts?.documents ?? 0,
        entityRef: placeRef,
      }),
    ],
    header: (entity, helpers) => <PlaceDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.name),
  },
  routes: jsfRoutes("spatial-unit"),
  home: {
    widgets: countCardWidgets({
      entityType: "place",
      count: "places",
      icon: "bi bi-geo-alt",
      label: t("entity.place.plural"),
      description: t("home.place.description"),
      className: "sia-welcome-card sia-spatial-unit",
      chipClassName: "spatial-unit-count-chip-alt",
      order: 20,
    }),
  },
};
