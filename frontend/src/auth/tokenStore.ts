/**
 * Holds the short-lived JWT used to call /api/v1/** from the embedded React panel.
 *
 * Deliberately in-memory only (module-level variable), never localStorage/sessionStorage: the token
 * is re-derived from the JSF session on every page load via POST /api/auth/session-token, and must not
 * outlive the tab or be reachable by other scripts on the page.
 */

export interface SessionToken {
  accessToken: string;
  tokenType: string;
  /** Epoch milliseconds at which this token stops being valid. */
  expiresAt: number;
}

let current: SessionToken | null = null;

export function setToken(token: SessionToken): void {
  current = token;
}

export function getToken(): SessionToken | null {
  return current;
}

export function clearToken(): void {
  current = null;
}

/** True once the token is within `marginMs` of expiry (default 30s), so callers can refresh ahead of time. */
export function isExpiringSoon(marginMs = 30_000): boolean {
  if (!current) return true;
  return Date.now() + marginMs >= current.expiresAt;
}
