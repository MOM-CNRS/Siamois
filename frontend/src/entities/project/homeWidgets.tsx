import { useQuery } from "@tanstack/react-query";
import { apiUrl } from "../../api/basePath";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";
import { listProjects } from "./api";
import { PROJECT_ROUTES } from "./routes";

// Home's two Project widgets (plan §4/§8 phase 7): "Projets récents" and the Project count card.
// Both current at HomePanel — the panelHome mirrors JSF's homePanel.xhtml only loosely, for two
// reasons found while implementing this phase, not guessed at:
//
// - JSF's "Mes derniers projets" (FlowBean.getMyActionUnits →
//   ActionUnitService.findByTeamMember) is a team-membership filter (a PROJECT-scoped Profile on
//   that specific action unit), not a recency sort — and GET /api/v1/projects has no membership
//   filter param at all, so there is no way to reproduce it, client-side or otherwise, from what
//   ProjectResource exposes. This widget is deliberately relabeled "Projets récents" and sorted
//   by creationTime:desc instead — a real, honest substitute, not a silent stand-in for "mine".
// - The count card's "créer" button is declared in JSF's own composite
//   (pages/shared/card/welcomeCard.xhtml's isCreateAllowed/btnNewAction attrs) but never wired to
//   an actual link — ActionUnitController has no standalone "new project" route (only
//   /spatial-unit/{id}/action-unit/new, scoped under a spatial unit, itself forwarding to the
//   dead flow.xhtml). Project creation instead goes through the list panel's own toolbar dialog
//   (plan §8 phase 8's actions.create bridge), so this card only gets "Voir la liste".
const RECENT_LIMIT = 5;

function useRecentProjects(organizationId?: number) {
  return useQuery({
    queryKey: ["project-home-recent", organizationId],
    queryFn: () => listProjects({ offset: 0, limit: RECENT_LIMIT, sort: "creationTime:desc", organizationId }),
  });
}

function RecentProjectsWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data, isLoading } = useRecentProjects(organizationId);
  const projects = data?.data ?? [];

  return (
    <div className="home-panel-widget-project-recent">
      <h3>Projets récents</h3>
      {isLoading && <div>Chargement…</div>}
      {!isLoading && projects.length === 0 && <div>Aucun projet</div>}
      {projects.length > 0 && (
        <ul>
          {projects.map((project) => (
            <li key={project.id}>
              <button type="button" className="p-button p-button-text" onClick={() => onNavigate?.("project", project.id)}>
                {project.fullIdentifier || project.identifier} — {project.name}
              </button>
              {project.mainLocation?.name && <span> · {project.mainLocation.name}</span>}
              <span> · {project._counts?.recordingUnits ?? 0} UE</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ProjectCountCardWidget({ organizationId }: HomeWidgetContext) {
  const { data, isLoading } = useRecentProjects(organizationId);

  return (
    // Class names mirror welcomeCard.xhtml's action-unit card (plan's class-name-preservation
    // rule) — PrimeReact's own theme doesn't define them, so this is inert until a future theme
    // pass, same acceptance as the rest of this migration.
    <div className="sia-welcome-card sia-action-unit">
      <i className="bi bi-arrow-down-square" />
      <div className="action-unit-count-chip-alt">{isLoading ? "…" : data?.totalCount ?? 0}</div>
      <div>Projets</div>
      <a className="action-unit-btn" href={apiUrl(PROJECT_ROUTES.list)}>
        Voir la liste
      </a>
    </div>
  );
}

export function projectHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [
    { key: "project-recent", render: () => <RecentProjectsWidget {...ctx} /> },
    { key: "project-count", render: () => <ProjectCountCardWidget {...ctx} /> },
  ];
}
