import { useQuery } from "@tanstack/react-query";
import { Panel } from "primereact/panel";
import { Card } from "primereact/card";
import { Chip } from "primereact/chip";
import { Button } from "primereact/button";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";
import { listProjects } from "./api";

// Home's two Project widgets (plan §4/§8 phase 7), each mirroring one real JSF piece with its
// PrimeReact counterpart rather than a from-scratch design:
// - RecentProjectsWidget mirrors panel/homePanel.xhtml's "myActionUnits" p:panel (a toggleable
//   panel of p:card tiles, one per project: icon+name, icon+location, icon+recording-unit-count,
//   a centered action button) → PrimeReact Panel + Card + Button, same structure.
// - ProjectCountCardWidget mirrors pages/shared/card/welcomeCard.xhtml (a p:card with an
//   icon+label+p:chip row, a description, and a centered footer button) → PrimeReact
//   Card + Chip + Button.
// "No custom theme" (plan §3) means no new CSS/--siamois-* mapping, not "invent a different
// design" — these use the same component choices JSF made, just PrimeReact's version of them.
// Legacy JSF class names (sia-welcome-card, action-unit-btn, ...) still ride along via
// `className` for a future theme pass.
//
// Two deliberate deviations from the JSF markup, both found while implementing this phase, not
// guessed at:
// - JSF's "Mes derniers projets" is a team-membership filter (FlowBean.getMyActionUnits →
//   ActionUnitService.findByTeamMember), not a recency sort, and GET /api/v1/projects has no
//   membership filter param — so this is relabeled "Projets récents" (creationTime:desc), a real
//   substitute, not a silent stand-in for "mine".
// - The welcomeCard's "créer" button is declared in JSF but never actually wired to a link
//   (ActionUnitController has no standalone "new project" route) — omitted rather than pointing
//   at a route that doesn't exist; project creation goes through the list panel's own toolbar
//   dialog (plan §8 phase 8's actions.create bridge).
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
    <Panel header="Projets récents" toggleable className="sia-form-panel">
      {isLoading && <div>Chargement…</div>}
      {!isLoading && projects.length === 0 && <div>Aucun projet</div>}
      {projects.length > 0 && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: "1rem" }}>
          {projects.map((project) => (
            <Card key={project.id} style={{ background: "var(--siamois-green-light-50)" }}>
              <div style={{ display: "flex", gap: "1em" }}>
                <i className="bi bi-arrow-down-square" style={{ color: "var(--context-main-color)" }} />
                <span>{project.name}</span>
              </div>
              <div style={{ display: "flex", gap: "1em" }}>
                <i className="bi bi-geo-alt" style={{ color: "var(--context-main-color)" }} />
                <span>{project.mainLocation?.name ?? "—"}</span>
              </div>
              <div style={{ display: "flex", gap: "1em" }}>
                <i className="bi bi-pencil-square" style={{ color: "var(--ground-main-color)" }} />
                <span>{project._counts?.recordingUnits ?? 0} enregistrements</span>
              </div>
              <Button
                label="Ouvrir le projet"
                onClick={() => onNavigate?.("project", project.id)}
                style={{
                  marginTop: "1em",
                  marginBottom: "1em",
                  display: "block",
                  marginLeft: "auto",
                  marginRight: "auto",
                  width: "fit-content",
                }}
              />
            </Card>
          ))}
        </div>
      )}
    </Panel>
  );
}

function ProjectCountCardWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data, isLoading } = useRecentProjects(organizationId);

  return (
    <Card className="sia-welcome-card sia-action-unit">
      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
        <i className="bi bi-arrow-down-square" />
        <span>Projets</span>
        <Chip label={isLoading ? "…" : String(data?.totalCount ?? 0)} className="action-unit-count-chip-alt" />
      </div>
      <div>
        <small>Interventions, opérations, fouilles, etc.</small>
      </div>
      <div style={{ display: "flex", justifyContent: "center", marginTop: "1em" }}>
        {/* No id → the list, same convention EntityListPanel's row click already uses (App's
            client-side router, not a page navigation — that's the whole point of this
            migration: no JSF round-trip just to switch panels). */}
        <Button label="Voir la liste" className="action-unit-btn" onClick={() => onNavigate?.("project")} />
      </div>
    </Card>
  );
}

export function projectHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [
    // Standalone panel ("Mes derniers projets" / here "Projets récents" — see the relabeling
    // note above), self-wrapped in its own <Panel> already.
    { key: "project-recent", render: () => <RecentProjectsWidget {...ctx} /> },
    // A tile for the shared "Accéder aux bases de données" panel/grid (dbAccessPanelGrid) —
    // HomePanel groups every "card"-kind widget from every entity into that one panel, never
    // gives this its own top-level panel.
    { key: "project-count", kind: "card", render: () => <ProjectCountCardWidget {...ctx} /> },
  ];
}
