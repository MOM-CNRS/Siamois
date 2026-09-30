import type { EntityTypeConfig } from "../types";
import { loadTypeCatalog } from "../typeCatalog";
import { relationTab } from "../../panels/relationTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getPhase, listPhases, patchPhaseAnswers } from "./api";
import { phaseColumns } from "./columns";
import { PhaseCreateForm } from "./CreateForm";
import { IdentifierTypeHeader } from "../../components/IdentifierTypeHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import type { PhaseDetail, PhaseSummary } from "./types";

// Phase's own EntityTypeConfig (migration plan, lot 2 "Phases") — registered so it can be looked
// up by key ("phase") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Its dynamic column catalog comes from the project's forms (entities/typeCatalog.ts).
export const phaseEntityConfig: EntityTypeConfig<PhaseSummary, PhaseDetail> = {
  key: "phase",
  labels: { singular: "Phase", plural: "Phases", all: "Toutes les phases" },
  collectionPath: "phases",
  // Matches PhaseTableDefinitionFactory/PhasePanel's own icon.
  icon: "bi bi-layers",
  panelClass: "phase-panel",
  api: {
    siblings: (id) => fetchSiblings("phases", id),
    get: getPhase,
    list: listPhases,
    patchAnswers: (id, answers) => patchPhaseAnswers(id, answers),
  },
  list: {
    // Dynamic columns: every field of the project's phase forms, additional ones included.
    schema: { load: (ctx) => loadTypeCatalog(ctx, "phase-types") },
    columns: phaseColumns,
    typesSegment: "phase-types",
    // Every list: newest first — the order the fiche's prev/next arrows walk (↓ = the row below).
    defaultSort: "creationTime:desc",
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
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="phase" typesSegment="phase-types" save={patchPhaseAnswers} onSaved={helpers.refetch} />
        ),
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
    header: (entity) => (
      <IdentifierTypeHeader entityType="phase" chipPrefix="phase" label={entity.label || entity.identifier} typeLabel={entity.type?.resolvedLabel} />
    ),
    chrome: (entity) => bookmarkChrome(entity, entity.identifier ?? entity.label),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: jsfRoutes("phase"),
  home: {
    widgets: countCardWidgets({
      entityType: "phase",
      count: "phases",
      icon: "bi bi-layers",
      label: "Phases",
      description: "Phases et sous-phases chronologiques",
      className: "sia-welcome-card sia-recording-unit",
      chipClassName: "recording-unit-count-chip-alt",
      order: 50,
    }),
  },
};
