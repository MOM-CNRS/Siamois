import type { EntityTypeConfig } from "../types";
import { loadTypeCatalog } from "../typeCatalog";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { duplicateFind, getFind, listFinds, patchFind, patchFindAnswers } from "./api";
import { findColumns } from "./columns";
import { FindCreateForm } from "./CreateForm";
import { SchemaEntityHeader } from "../../components/SchemaEntityHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import { documentsTab } from "../document/documentsTab";
import type { FindDetail, FindSummary } from "./types";
import { t } from "../../i18n";

// Find's own EntityTypeConfig (migration plan, lot 1 "Mobilier") — registered so it can be looked
// up by key ("find") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Its dynamic column catalog comes from the project's forms (entities/typeCatalog.ts).
export const findEntityConfig: EntityTypeConfig<FindSummary, FindDetail> = {
  key: "find",
  labels: { singular: t("entity.find.singular"), plural: t("entity.find.plural"), all: t("entity.find.all") },
  collectionPath: "finds",
  // Matches SpecimenTableDefinitionFactory/SpecimenPanel's own icon.
  icon: "bi bi-bucket",
  panelClass: "specimen-panel",
  api: {
    siblings: (id) => fetchSiblings("finds", id),
    get: getFind,
    list: listFinds,
    patchAnswers: (id, answers) => patchFindAnswers(id, answers),
    duplicate: duplicateFind,
  },
  list: {
    // Dynamic columns: every field of the project's find forms, additional ones included.
    schema: { load: (ctx) => loadTypeCatalog(ctx, "find-types") },
    columns: findColumns,
    typesSegment: "find-types",
    // Every list: newest first — the order the fiche's prev/next arrows walk (↓ = the row below).
    defaultSort: "creationTime:desc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc for
    // why it needs its own recording-unit picker on top of the usual type/category one.
    createForm: (ctx) => <FindCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "find",
    // Created from a recording unit's field: ON that recording unit, as JSF's "new find" row action.
    createPrefillFrom: ({ entityType, entityId, entityLabel }) =>
      entityType === "recordingUnit" && entityId != null
        ? { recordingUnit: { id: entityId, label: entityLabel ?? String(entityId) } }
        : undefined,
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="find" typesSegment="find-types" save={patchFindAnswers} onSaved={helpers.refetch} />
        ),
      },
      documentsTab<FindDetail>({
        scopeEntityType: "find",
        segment: "finds",
        linkField: "findIds",
        badge: (entity) => entity._counts?.documents ?? 0,
        projectId: (entity) => entity.projectId,
        entityRef: (entity) => ({ id: entity.id, label: entity.fullIdentifier || String(entity.id) }),
      }),
    ],
    header: (entity, helpers) => (
      <SchemaEntityHeader
        entityType="find"
        chipPrefix="specimen"
        entity={entity}
        identifier={entity.fullIdentifier}
        typesSegment="find-types"
        patch={patchFind}
        onSaved={helpers.refetch}
      />
    ),
    chrome: (entity) => bookmarkChrome(entity, entity.fullIdentifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: jsfRoutes("specimen"),
  home: {
    widgets: countCardWidgets({
      entityType: "find",
      count: "finds",
      icon: "bi bi-bucket",
      label: t("entity.find.plural"),
      description: t("home.find.description"),
      className: "sia-welcome-card sia-specimen",
      chipClassName: "specimen-count-chip-alt",
      order: 40,
    }),
  },
};
