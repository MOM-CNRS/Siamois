import { fetchSessionToken } from "../auth/sessionAuth";
import { getToken, isExpiringSoon } from "../auth/tokenStore";
import { getBasePath } from "./basePath";

let csrfTokenForRefresh: string | null = null;

/** Called once at mount time (see mount.ts) so ensureFreshToken() can silently refresh later. */
export function configureTokenRefresh(csrfToken: string): void {
  csrfTokenForRefresh = csrfToken;
}

async function ensureFreshToken(): Promise<string> {
  if (isExpiringSoon()) {
    if (!csrfTokenForRefresh) {
      throw new Error("Token refresh requested before configureTokenRefresh() was called");
    }
    await fetchSessionToken(csrfTokenForRefresh);
  }
  const token = getToken();
  if (!token) {
    throw new Error("No session token available");
  }
  return token.accessToken;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown,
  ) {
    super(`API request failed with status ${status}`);
  }
}

/** Thin fetch wrapper for /api/v1/**: attaches the Bearer token and refreshes it ahead of expiry. */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const accessToken = await ensureFreshToken();
  const response = await fetch(`${getBasePath()}/api/v1${path}`, {
    ...init,
    headers: {
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      Authorization: `Bearer ${accessToken}`,
      ...init.headers,
    },
  });

  if (!response.ok) {
    let body: unknown;
    try {
      body = await response.json();
    } catch {
      body = undefined;
    }
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
