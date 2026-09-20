import type { ProjectDetail } from "./types";

// Registers the fiche tab slot (plan §4/§8 phase 4) with a plain read-only summary. The real
// schema-driven form (GET /api/v1/organizations/{id}/project-types, the field-renderer registry,
// identifier inline-edit, validate toggle, revision history) is phase 6 — don't mistake this for
// that; it exists now only so EntityDetailPanel has a tab to render and the registry/routes/api
// wiring for phase 5 (List) has something real to navigate into.
export function ProjectFicheTab({ entity }: { entity: ProjectDetail }) {
  return (
    <dl className="project-fiche-tab">
      <dt>Nom</dt>
      <dd>{entity.name}</dd>
      <dt>Identifiant</dt>
      <dd>{entity.fullIdentifier || entity.identifier}</dd>
      <dt>Type</dt>
      <dd>{entity.type?.resolvedLabel ?? "—"}</dd>
      <dt>Localisation</dt>
      <dd>{entity.mainLocation?.name ?? "—"}</dd>
    </dl>
  );
}
