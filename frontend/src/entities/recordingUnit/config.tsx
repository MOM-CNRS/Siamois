import type { EntityRef, EntityTypeConfig, ListScope } from "../types";
import { relationTab } from "../../panels/relationTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { scopeProjectId } from "../scope";
import { duplicateRecordingUnit, getRecordingUnit, listRecordingUnits, patchRecordingUnitAnswers } from "./api";
import { recordingUnitColumns } from "./columns";
import { RecordingUnitCreateForm } from "./CreateForm";
import { RecordingUnitDetailHeader } from "./DetailHeader";
import { RecordingUnitFicheTab } from "./FicheTab";
import { getOrganizationRecordingUnitTypes, getRecordingUnitTypes } from "./recordingUnitTypes";
import { RECORDING_UNIT_ROUTES } from "./routes";
import { recordingUnitHomeWidgets } from "./homeWidgets";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";

function recordingUnitRef(ru: RecordingUnitSummary): EntityRef {
  return { id: ru.id, label: ru.fullIdentifier };
}

// A new UE or find created from this UE belongs to the same project.
function projectScope(ru: RecordingUnitSummary): ListScope | undefined {
  return ru.projectId ? { entityType: "project", id: ru.projectId } : undefined;
}

// RecordingUnit's own EntityTypeConfig (plan: generic related-list tab) — registered so it can be
// looked up by key ("recordingUnit") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// detail.tabs/header now built (fiche + identifier/category header, mirroring Project's own —
// see FicheTab.tsx/DetailHeader.tsx for what's deliberately still absent and why). Prev/next is
// scoped to the RU's own project (GET /recording-units/{id}/siblings), not an organization the way
// Project's is.
export const recordingUnitEntityConfig: EntityTypeConfig<RecordingUnitSummary, RecordingUnitDetail> = {
  key: "recordingUnit",
  labels: { singular: "Unité d'enregistrement", plural: "Unités d'enregistrement" },
  collectionPath: "recording-units",
  // Matches RecordingUnitTableDefinitionFactory's own identifierCol iconClass.
  icon: "bi bi-pencil-square",
  panelClass: "recording-unit-panel",
  api: {
    siblings: (id) => fetchSiblings("recording-units", id),
    get: getRecordingUnit,
    list: listRecordingUnits,
    patchAnswers: (id, answers) => patchRecordingUnitAnswers(id, answers),
    duplicate: duplicateRecordingUnit,
  },
  list: {
    columns: recordingUnitColumns,
    // A project's own effective RU form(s) when the list has a project (EntityListPanel forwards its
    // own `scope` prop here — see relationTab/entities/types.ts's ListParams.scope); the
    // organization's aggregate of every project's forms for the organization-wide list.
    schema: {
      load: async ({ scope, organizationId }) => {
        const projectId = scopeProjectId(scope);
        let types;
        if (projectId != null) types = await getRecordingUnitTypes(projectId);
        else if (!scope && organizationId != null) types = await getOrganizationRecordingUnitTypes(organizationId);
        else return { fields: {}, columns: [] };
        return { fields: types.fields, columns: types.tableColumns };
      },
    },
    defaultSort: "creationTime:desc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <RecordingUnitCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "recordingUnit",
    // JSF's RecordingUnitTableViewModel row actions, after the generic bookmark/duplicate.
    rowActions: [
      {
        key: "new-parent",
        icon: "bi bi-node-plus-fill rotate-minus90",
        tooltip: "Créer une UE parente",
        run: (row, ctx) => ctx.openCreate("recordingUnit", { scope: projectScope(row), prefill: { child: recordingUnitRef(row) } }),
      },
      {
        key: "new-child",
        icon: "bi bi-node-plus-fill rotate-90",
        tooltip: "Créer une UE enfant",
        run: (row, ctx) => ctx.openCreate("recordingUnit", { scope: projectScope(row), prefill: { parent: recordingUnitRef(row) } }),
      },
      {
        key: "new-find",
        icon: "bi bi-bucket",
        tooltip: "Créer un mobilier",
        run: (row, ctx) => ctx.openCreate("find", { scope: projectScope(row), prefill: { recordingUnit: recordingUnitRef(row) } }),
      },
    ],
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity, helpers) => <RecordingUnitFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
      // JSF's hierarchy tab, reduced to what the recording unit contains (its direct children).
      relationTab<RecordingUnitDetail>({
        key: "children",
        label: "Unités d'enregistrement",
        target: "recordingUnit",
        scopeEntityType: "recordingUnit",
        path: "children",
        projectId: (entity) => entity.projectId,
        createPrefill: (entity) => ({ parent: recordingUnitRef(entity) }),
        badge: (entity) => entity._counts?.children ?? 0,
      }),
      // JSF's SpecimenTab. REST segment "mobiliers", not Find's own collectionPath.
      relationTab<RecordingUnitDetail>({
        key: "finds",
        label: "Mobilier",
        target: "find",
        scopeEntityType: "recordingUnit",
        path: "mobiliers",
        projectId: (entity) => entity.projectId,
        createPrefill: (entity) => ({ recordingUnit: recordingUnitRef(entity) }),
        badge: (entity) => entity._counts?.finds ?? 0,
      }),
    ],
    header: (entity, helpers) => <RecordingUnitDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.fullIdentifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: RECORDING_UNIT_ROUTES,
  home: {
    widgets: recordingUnitHomeWidgets,
  },
};
