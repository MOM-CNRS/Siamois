import {apiFetch} from "../../api/client";

/**
 * DELETE /api/v1/{collectionPath}/{id}. Answers 204, or 409 with a readable message when the
 * entity still has relationships (or, for a project, content tied to another project): nothing
 * is deleted then.
 */
export async function deleteEntity(collectionPath: string, id: string | number): Promise<void> {
  await apiFetch<unknown>(`/api/v1/${collectionPath}/${id}`, { method: "DELETE" });
}
