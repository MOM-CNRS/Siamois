import type { EntityTypeConfig } from "../types";
import { getRecordingUnit, listRecordingUnits, patchRecordingUnitAnswers } from "./api";
import { recordingUnitColumns } from "./columns";
import { RecordingUnitCreateForm } from "./CreateForm";
import { RecordingUnitDetailHeader } from "./DetailHeader";
import { RecordingUnitFicheTab } from "./FicheTab";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import { RECORDING_UNIT_ROUTES } from "./routes";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";

// RecordingUnit's own EntityTypeConfig (plan: generic related-list tab) — registered so it can be
// looked up by key ("recordingUnit") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// detail.tabs/header now built (fiche + identifier/category header, mirroring Project's own —
// see FicheTab.tsx/DetailHeader.tsx for what's deliberately still absent and why). Still NOT
// built: `api.siblings` (no scoped `/recording-units/{id}/siblings` endpoint yet — RU's prev/next
// would need to be scoped to its own action unit, not an institution the way Project's is) and
// `detail.chrome` (no `resourceUri` on RecordingUnitResource yet to derive a bookmark target
// from) — both out of scope for "the RU fiche renders instead of a blank overview panel".
export const recordingUnitEntityConfig: EntityTypeConfig<RecordingUnitSummary, RecordingUnitDetail> = {
  key: "recordingUnit",
  labels: { singular: "Unité d'enregistrement", plural: "Unités d'enregistrement" },
  collectionPath: "recording-units",
  // Matches RecordingUnitTableDefinitionFactory's own identifierCol iconClass.
  icon: "bi bi-pencil-square",
  api: {
    get: getRecordingUnit,
    list: listRecordingUnits,
    patchAnswers: (id, answers) => patchRecordingUnitAnswers(id, answers),
  },
  list: {
    columns: recordingUnitColumns,
    // Project-scoped, not organization-scoped (unlike Project's own list.schema): a project's own
    // effective RU form(s). EntityListPanel forwards its own `scope` prop here — see
    // relationTab/entities/types.ts's ListParams.scope.
    schema: {
      load: async ({ scope }) => {
        if (scope == null) return { fields: {}, columns: [] };
        const types = await getRecordingUnitTypes(scope.id);
        return { fields: types.fields, columns: types.tableColumns };
      },
    },
    defaultSort: "creationTime:desc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <RecordingUnitCreateForm {...ctx} />,
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity, helpers) => <RecordingUnitFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
    ],
    header: (entity, helpers) => <RecordingUnitDetailHeader entity={entity} onSaved={helpers.refetch} />,
  },
  routes: RECORDING_UNIT_ROUTES,
};
