import { relationTab } from "../../panels/relationTab";
import type { EntityTypeConfig } from "../types";
import { getProject, listProjects, patchProject } from "./api";
import { projectColumns } from "./columns";
import { ProjectDetailHeader } from "./DetailHeader";
import { ProjectFicheTab } from "./FicheTab";
import { projectHomeWidgets } from "./homeWidgets";
import { getProjectTypes } from "./projectTypes";
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
  collectionPath: "projects",
  // Matches ActionUnitPanel/ActionUnitListPanel's own AbstractPanel.icon exactly.
  icon: "bi bi-arrow-down-square",
  api: {
    // fields=all so the detail response carries an `answers` map for the WHOLE field catalog, not
    // just the seven properties ProjectResource exposes flat — that map is what lets the fiche
    // render all 33 of ActionUnit.DETAILS_FORM's fields with real values (FicheTab.tsx). The list
    // asks for a narrower projection instead, driven by its visible columns.
    get: (id) => getProject(id, "all"),
    list: listProjects,
    patchAnswers: (id, answers) => patchProject(id, { answers }),
  },
  list: {
    // JSF's own free-text box for this list is present but disabled (ActionUnitListPanel relies
    // on per-column filters instead, which GET /api/v1/projects has no params for — see
    // columns.tsx). The REST `search` param is real and already matches name/identifier/
    // fullIdentifier (ActionUnitSpec.projectSearch), so enabling it here is a deliberate
    // improvement the API allows, not a mismatch with JSF's current behavior.
    columns: projectColumns,
    // Drives the dynamic columns, the column toggler and the `fields=` projection param — the
    // catalog is organization-scoped (GET /api/v1/organizations/{id}/project-types), so it's
    // reloaded whenever EntityListPanel's own organizationId changes, same as the list query.
    schema: {
      load: async ({ organizationId }) => {
        if (organizationId == null) return { fields: {}, columns: [] };
        const types = await getProjectTypes(organizationId);
        return {
          fields: types.fields,
          columns: types.tableColumns,
        };
      },
    },
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
      // actionUnitTabView.xhtml's own order is détails, documents, UE, contenants, phases; only
      // the fiche and this one are migrated so far, so UE comes right after détails for now.
      relationTab<ProjectDetail>({
        key: "recording-units",
        label: "Unités d'enregistrement",
        target: "recordingUnit",
        scopeEntityType: "project",
        badge: (entity) => entity._counts?.recordingUnits ?? 0,
      }),
    ],
    // actionUnitPanelHeader.xhtml's content, rendered inside EntityDetailPanel's own panel header
    // (plan §7/§8, "toolbar is part of the panel header" — the header and the generic toolbar
    // share one titlebar, exactly like the real markup).
    header: (entity, helpers) => <ProjectDetailHeader entity={entity} onSaved={helpers.refetch} />,
    // Derives the overview toolbar's chrome client-side, for an overview opened by
    // EntityListPanel's onOpenOverview (plan §8 phase 5) rather than seeded from MountOptions.
    // resourceUri comes straight off the API (ActionUnitPanel.ressourceUri() server-side) — never
    // hardcode the "/action-unit/" prefix here.
    chrome: (entity) => ({
      resourceUri: entity.resourceUri ?? "",
      title: entity.fullIdentifier || entity.name,
      bookmarked: entity.bookmarked ?? false,
    }),
  },
  routes: PROJECT_ROUTES,
  home: {
    widgets: projectHomeWidgets,
  },
};
