import type { EntityTypeConfig } from "../types";
import { getProject, listProjects } from "./api";
import { projectColumns } from "./columns";
import { ProjectDetailHeader } from "./DetailHeader";
import { ProjectFicheTab } from "./FicheTab";
import { projectHomeWidgets } from "./homeWidgets";
import { PROJECT_ROUTES } from "./routes";
import type { ProjectDetail, ProjectSummary } from "./types";

// The only Project-specific file this phase produces (plan §3/§4/§8 phase 4) — everything it
// plugs into (EntityListPanel, EntityDetailPanel, HomePanel, the registry mechanism itself) is
// entity-agnostic and shipped in phase 2. Routes mirror the real redirection-controller URLs
// (ActionUnitController: GET /action-unit, GET /action-unit/{id}) — plain navigation, no
// client-side router, per the plan's "React does not own global routing" rule (§7.4).
export const projectEntityConfig: EntityTypeConfig<ProjectSummary, ProjectDetail> = {
  key: "project",
  labels: { singular: "Projet", plural: "Projets" },
  // Matches ActionUnitPanel/ActionUnitListPanel's own AbstractPanel.icon exactly.
  icon: "bi bi-arrow-down-square",
  api: {
    list: listProjects,
    get: getProject,
  },
  list: {
    // JSF's own free-text box for this list is present but disabled (ActionUnitListPanel relies
    // on per-column filters instead, which GET /api/v1/projects has no params for — see
    // columns.tsx). The REST `search` param is real and already matches name/identifier/
    // fullIdentifier (ActionUnitSpec.projectSearch), so enabling it here is a deliberate
    // improvement the API allows, not a mismatch with JSF's current behavior.
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
        render: (entity, helpers) => <ProjectFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
    ],
    // actionUnitPanelHeader.xhtml's content, rendered inside EntityDetailPanel's own panel header
    // (plan §7/§8, "toolbar is part of the panel header" — the header and the generic toolbar
    // share one titlebar, exactly like the real markup).
    header: (entity, helpers) => <ProjectDetailHeader entity={entity} onSaved={helpers.refetch} />,
  },
  routes: PROJECT_ROUTES,
  home: {
    widgets: projectHomeWidgets,
  },
};
