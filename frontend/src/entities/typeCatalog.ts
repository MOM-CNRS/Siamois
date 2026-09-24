import { apiFetch } from "../api/client";
import type { FieldResource } from "../fields/types";
import { scopeProjectId } from "./scope";
import type { FieldCatalog, ListScope } from "./types";

interface TypesResponseBody {
  data?: { fields?: Record<string, FieldResource> }[];
  _default?: { fields?: Record<string, FieldResource> };
}

/**
 * A list's column catalog built from a project's per-type forms (GET /api/v1/projects/{id}/
 * phase-types, container-types): every field any of its types shows — system fields and each
 * type's additional ones — as a toggleable column, hidden until picked. Each field carries what the
 * list accepts on it (FieldResource.query). A list with no project in scope (organization-wide)
 * has no catalog: a form belongs to a project.
 */
export async function loadTypeCatalog(scope: ListScope | undefined, segment: string): Promise<FieldCatalog> {
  const projectId = scopeProjectId(scope);
  if (projectId == null) return { fields: {}, columns: [] };
  const body = await apiFetch<TypesResponseBody>(`/api/v1/projects/${projectId}/${segment}`);
  const fields: Record<string, FieldResource> = {};
  // System fields first, then each type's additional ones. JSON objects put integer-like keys (the
  // additional fields' positive ids) ahead of the rest whatever the server's order, hence the list
  // and the explicit (stable) sort.
  const order: string[] = [];
  for (const source of [body._default?.fields, ...(body.data ?? []).map((t) => t.fields)]) {
    for (const field of Object.values(source ?? {})) {
      if (!(field.id in fields)) order.push(field.id);
      fields[field.id] = field;
    }
  }
  order.sort((a, b) => Number(fields[b].isSystemField) - Number(fields[a].isSystemField));
  return {
    fields,
    columns: order.map((fieldId, index) => ({ fieldId, columnId: fieldId, visible: false, order: index })),
  };
}
