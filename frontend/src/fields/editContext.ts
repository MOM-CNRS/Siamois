/**
 * Where a field is being edited: what a relation picker needs besides the field itself — the
 * organization for org-wide sources (persons, projects, places, concepts), the project the edited
 * entity lives in for project-scoped ones (recording units, finds, phases, containers), and the
 * edited entity itself so a « Nouveau » created from the field can be linked back to it (a find
 * created from a recording unit's field is created ON that recording unit).
 *
 * <p>Derived from the row/entity being edited rather than threaded through every fiche: every
 * entity resource carries its project the same way ({@code projectId} on a detail/project-scoped
 * row, {@code project} ResourceRef on an organization-wide row), and a project is its own.</p>
 */
export interface FieldEditContext {
  organizationId?: number;
  projectId?: string;
  entityType?: string;
  entityId?: string | number;
  entityLabel?: string;
}

interface EntityLike {
  id?: string | number;
  projectId?: string | number | null;
  project?: { resourceId?: string | number } | null;
  fullIdentifier?: string | null;
  identifier?: string | null;
  name?: string | null;
  title?: string | null;
}

export function editContextOf(row: unknown, entityType: string | undefined, organizationId: number | undefined): FieldEditContext {
  const entity = (row ?? {}) as EntityLike;
  const projectId =
    entityType === "project"
      ? entity.id
      : entity.projectId ?? entity.project?.resourceId ?? undefined;
  return {
    organizationId,
    projectId: projectId != null ? String(projectId) : undefined,
    entityType,
    entityId: entity.id,
    entityLabel: entity.fullIdentifier ?? entity.identifier ?? entity.name ?? entity.title ?? undefined,
  };
}
