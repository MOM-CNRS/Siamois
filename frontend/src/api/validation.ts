import { apiFetch } from "./client";
import type { ValidationStatusValue } from "../components/table/ValidationStatusBadge";

// The status rides every entity's own PATCH (`validated`), not a dedicated endpoint: the server
// applies the rights rule (edit right for en cours/terminé/annulé, validator right for validé).
export async function patchValidation(collectionPath: string, id: string | number, status: ValidationStatusValue): Promise<void> {
  await apiFetch<unknown>(`/api/v1/${collectionPath}/${id}`, { method: "PATCH", body: { validated: status } });
}

// Same rule as ValidationStatus#requiresValidatorTo server-side: reaching "validé", or leaving it,
// takes the validator right; every other change is an editor's.
export function requiresValidator(from: ValidationStatusValue, to: ValidationStatusValue): boolean {
  return from !== to && (to === "VALIDATED" || from === "VALIDATED");
}
