import type { EntityTypeConfig } from "../types";
import { getRecordingUnit, listRecordingUnits, patchRecordingUnitAnswers } from "./api";
import { recordingUnitColumns } from "./columns";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import { RECORDING_UNIT_ROUTES } from "./routes";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";

// RecordingUnit's own EntityTypeConfig (plan: generic related-list tab) — registered so it can be
// looked up by key ("recordingUnit") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string. No
// detail tabs/header/chrome yet: RecordingUnit has no fiche of its own in React today (plan phase
// D2), so opening one in the overview pane falls back to EntityDetailPanel's "Unknown entity type"
// message until that lands.
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
  },
  detail: {
    tabs: [],
  },
  routes: RECORDING_UNIT_ROUTES,
};
