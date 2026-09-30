import { ApiError } from "./client";

/**
 * The sentence shown to the user for a failed call. A server message is kept when there is one
 * (the API answers 400/409 with a readable explanation); otherwise the status gives a generic one,
 * and `fallback` names the action that failed ("Échec de la création").
 */
export function messageForError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return "Vous n'avez pas le droit d'effectuer cette action.";
    if (error.status === 404) return "Cet élément n'existe plus.";
    if (error.status >= 500) return `${fallback} : erreur du serveur, réessayez dans un instant.`;
    return error.message || fallback;
  }
  if (error instanceof TypeError) return `${fallback} : le serveur est injoignable.`;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
