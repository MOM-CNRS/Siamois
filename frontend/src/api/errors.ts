import { ApiError } from "./client";
import { t } from "../i18n";

/**
 * The sentence shown to the user for a failed call. A server message is kept when there is one
 * (the API answers 400/409 with a readable explanation); otherwise the status gives a generic one,
 * and `fallback` names the action that failed ("Échec de la création").
 */
export function messageForError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return t("error.forbidden");
    if (error.status === 404) return t("error.notFound");
    if (error.status >= 500) return t("error.server", { action: fallback });
    return error.message || fallback;
  }
  if (error instanceof TypeError) return t("error.unreachable", { action: fallback });
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
