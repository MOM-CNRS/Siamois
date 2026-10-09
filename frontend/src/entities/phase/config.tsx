import type { EntityTypeConfig } from "../types";
import { loadTypeCatalog } from "../typeCatalog";
import { relationTab } from "../../panels/relationTab";
import { documentsTab } from "../document/documentsTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getPhase, listPhases, patchPhase, patchPhaseAnswers } from "./api";
import { phaseColumns } from "./columns";
import { PhaseCreateForm } from "./CreateForm";
import { SchemaEntityHeader } from "../../components/SchemaEntityHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import type { PhaseDetail, PhaseSummary } from "./types";
import { t } from "../../i18n";

// Phase's own EntityTypeConfig (migration plan, lot 2 "Phases") — registered so it can be looked
// up by key ("phase") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Its dynamic column catalog comes from the project's forms (entities/typeCatalog.ts).
export const phaseEntityConfig: EntityTypeConfig<PhaseSummary, PhaseDetail> = {
  key: "phase",
  labels: { singular: t("entity.phase.singular"), plural: t("entity.phase.plural"), all: t("entity.phase.all") },
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
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="phase" typesSegment="phase-types" save={patchPhaseAnswers} onSaved={helpers.refetch} />
        ),
      },
      documentsTab<PhaseDetail>({
        scopeEntityType: "phase",
        segment: "phases",
        linkField: "phaseIds",
        badge: (entity) => entity._counts?.documents ?? 0,
        projectId: (entity) => entity.projectId,
        entityRef: (entity) => ({ id: entity.id, label: entity.label || entity.identifier || String(entity.id) }),
      }),
      relationTab<PhaseDetail>({
        key: "recording-units",
        label: t("entity.recordingUnit.plural"),
        target: "recordingUnit",
        scopeEntityType: "phase",
        path: "recording-units",
        projectId: (entity) => entity.projectId,
        creatable: false,
      }),
    ],
    header: (entity, helpers) => (
      <SchemaEntityHeader
        entityType="phase"
        chipPrefix="phase"
        entity={entity}
        identifier={entity.identifier ?? ""}
        title={{ value: entity.title }}
        typesSegment="phase-types"
        patch={patchPhase}
        onSaved={helpers.refetch}
      />
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
      label: t("entity.phase.plural"),
      description: t("home.phase.description"),
      className: "sia-welcome-card sia-recording-unit",
      chipClassName: "recording-unit-count-chip-alt",
      order: 50,
    }),
  },
};
