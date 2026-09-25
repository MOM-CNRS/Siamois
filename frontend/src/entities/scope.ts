import type { ListScope } from "./types";

// The project a scoped list lives in: the scope itself when it IS a project (a project's own
// relation tabs), otherwise the project its relationTab passed along (a recording unit's children,
// a phase's recording units). Undefined for an unscoped (organization-wide) list, or a scope that
// has no project at all (a place's children).
export function scopeProjectId(scope: ListScope | undefined): string | undefined {
  if (!scope) return undefined;
  if (scope.entityType === "project") return String(scope.id);
  return scope.projectId != null ? String(scope.projectId) : undefined;
}
