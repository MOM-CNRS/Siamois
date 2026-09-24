import type { EntityTypeConfig } from "../types";
import { relationTab } from "../../panels/relationTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getPhase, listPhases, patchPhaseAnswers } from "./api";
import { phaseColumns } from "./columns";
import { PhaseCreateForm } from "./CreateForm";
import { PhaseDetailHeader } from "./DetailHeader";
import { PhaseFicheTab } from "./FicheTab";
import { PHASE_ROUTES } from "./routes";
import { phaseHomeWidgets } from "./homeWidgets";
import type { PhaseDetail, PhaseSummary } from "./types";

// Phase's own EntityTypeConfig (migration plan, lot 2 "Phases") — registered so it can be looked
// up by key ("phase") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Reduced scope vs RecordingUnit's own config, same precedent set for Find in lot 1: no
// `list.schema` (pinned columns only, no dynamic column catalog/toggler yet).
export const phaseEntityConfig: EntityTypeConfig<PhaseSummary, PhaseDetail> = {
  key: "phase",
  labels: { singular: "Phase", plural: "Phases" },
  collectionPath: "phases",
  // Matches PhaseTableDefinitionFactory/PhasePanel's own icon.
  icon: "bi bi-layers",
  api: {
    siblings: (id) => fetchSiblings("phases", id),
    get: getPhase,
    list: listPhases,
    patchAnswers: (id, answers) => patchPhaseAnswers(id, answers),
  },
  list: {
    columns: phaseColumns,
    defaultSort: "orderNumber:asc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <PhaseCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "phase",
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity, helpers) => <PhaseFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
      relationTab<PhaseDetail>({
        key: "recording-units",
        label: "Unités d'enregistrement",
        target: "recordingUnit",
        scopeEntityType: "phase",
        path: "recording-units",
        projectId: (entity) => entity.projectId,
        creatable: false,
      }),
    ],
    header: (entity, helpers) => <PhaseDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.identifier ?? entity.label),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: PHASE_ROUTES,
  home: {
    widgets: phaseHomeWidgets,
  },
};
