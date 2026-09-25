// In-memory JWT store for the mounted React app's lifetime. Deliberately not persisted
// (localStorage/sessionStorage) — the token is re-minted from the JSF session on every mount via
// sessionAuth.ts, and the JSF session itself is already the durable credential.
interface StoredToken {
  accessToken: string;
  // epoch ms; refresh a little before actual expiry to avoid a request racing the deadline
  expiresAt: number;
}

let current: StoredToken | null = null;

const REFRESH_SKEW_MS = 30_000;

export function setToken(accessToken: string, expiresInSeconds: number): void {
  current = {
    accessToken,
    expiresAt: Date.now() + expiresInSeconds * 1000,
  };
}

export function clearToken(): void {
  current = null;
}

export function getValidToken(): string | null {
  if (!current) return null;
  if (Date.now() >= current.expiresAt - REFRESH_SKEW_MS) return null;
  return current.accessToken;
}
