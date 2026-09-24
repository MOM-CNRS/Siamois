import type { EntityTypeConfig } from "../types";
import { relationTab } from "../../panels/relationTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { scopeProjectId } from "../scope";
import { duplicateRecordingUnit, getRecordingUnit, listRecordingUnits, patchRecordingUnitAnswers } from "./api";
import { recordingUnitColumns } from "./columns";
import { RecordingUnitCreateForm } from "./CreateForm";
import { RecordingUnitDetailHeader } from "./DetailHeader";
import { RecordingUnitFicheTab } from "./FicheTab";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import { RECORDING_UNIT_ROUTES } from "./routes";
import { recordingUnitHomeWidgets } from "./homeWidgets";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";

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
  api: {
    siblings: (id) => fetchSiblings("recording-units", id),
    get: getRecordingUnit,
    list: listRecordingUnits,
    patchAnswers: (id, answers) => patchRecordingUnitAnswers(id, answers),
    duplicate: duplicateRecordingUnit,
  },
  list: {
    columns: recordingUnitColumns,
    // Project-scoped, not organization-scoped (unlike Project's own list.schema): a project's own
    // effective RU form(s). EntityListPanel forwards its own `scope` prop here — see
    // relationTab/entities/types.ts's ListParams.scope.
    schema: {
      load: async ({ scope }) => {
        const projectId = scopeProjectId(scope);
        if (projectId == null) return { fields: {}, columns: [] };
        const types = await getRecordingUnitTypes(projectId);
        return { fields: types.fields, columns: types.tableColumns };
      },
    },
    defaultSort: "creationTime:desc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <RecordingUnitCreateForm {...ctx} />,
    // JSF's own organization-wide list disables creation too (ToolbarCreateConfig
    // createAllowedSupplier false): the form needs the project this list has no scope for.
    createRequiresScope: "La création d'UE n'est disponible que depuis un projet.",
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
        creatable: false,
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
        creatable: false,
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
