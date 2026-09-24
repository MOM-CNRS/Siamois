import type { EntityTypeConfig } from "../types";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getFind, listFinds, patchFindAnswers } from "./api";
import { findColumns } from "./columns";
import { FindCreateForm } from "./CreateForm";
import { FindDetailHeader } from "./DetailHeader";
import { FindFicheTab } from "./FicheTab";
import { FIND_ROUTES } from "./routes";
import { findHomeWidgets } from "./homeWidgets";
import type { FindDetail, FindSummary } from "./types";

// Find's own EntityTypeConfig (migration plan, lot 1 "Mobilier") — registered so it can be looked
// up by key ("find") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Reduced scope vs RecordingUnit's own config (see the migration plan's lot 1 section): no
// `list.schema` (pinned columns only, no dynamic column catalog/toggler yet — GET
// /api/v1/projects/{id}/find-types is only consulted by the fiche, per-entity, not batched for a
// list page).
export const findEntityConfig: EntityTypeConfig<FindSummary, FindDetail> = {
  key: "find",
  labels: { singular: "Mobilier", plural: "Mobilier" },
  collectionPath: "finds",
  // Matches SpecimenTableDefinitionFactory/SpecimenPanel's own icon.
  icon: "bi bi-bucket",
  api: {
    siblings: (id) => fetchSiblings("finds", id),
    get: getFind,
    list: listFinds,
    patchAnswers: (id, answers) => patchFindAnswers(id, answers),
  },
  list: {
    columns: findColumns,
    defaultSort: "fullIdentifier:asc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc for
    // why it needs its own recording-unit picker on top of the usual type/category one.
    createForm: (ctx) => <FindCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "find",
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity, helpers) => <FindFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
    ],
    header: (entity, helpers) => <FindDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.fullIdentifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: FIND_ROUTES,
  home: {
    widgets: findHomeWidgets,
  },
};
