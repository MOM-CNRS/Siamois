import type { EntityTypeConfig } from "../types";
import { getProject, listProjects } from "./api";
import { projectColumns } from "./columns";
import { ProjectFicheTab } from "./FicheTab";
import type { ProjectDetail, ProjectSummary } from "./types";

// The only Project-specific file this phase produces (plan §3/§4/§8 phase 4) — everything it
// plugs into (EntityListPanel, EntityDetailPanel, HomePanel, the registry mechanism itself) is
// entity-agnostic and shipped in phase 2. Routes mirror the real redirection-controller URLs
// (ActionUnitController: GET /action-unit, GET /action-unit/{id}) — plain navigation, no
// client-side router, per the plan's "React does not own global routing" rule (§7.4).
export const projectEntityConfig: EntityTypeConfig<ProjectSummary, ProjectDetail> = {
  key: "project",
  labels: { singular: "Projet", plural: "Projets" },
  api: {
    list: listProjects,
    get: getProject,
  },
  list: {
    columns: projectColumns,
    defaultSort: "name:asc",
    searchable: true,
  },
  detail: {
    // Fiche tab only, phase 1 (plan §2/§4) — relationship tabs (recording units, containers,
    // phases), Documents and Stratigraphy are deliberately not registered here.
    tabs: [
      {
        key: "fiche",
        label: "Détails",
        render: (entity) => <ProjectFicheTab entity={entity} />,
      },
    ],
  },
  routes: {
    list: "/action-unit",
    detail: (id) => `/action-unit/${id}`,
  },
};
