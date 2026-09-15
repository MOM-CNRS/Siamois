import { setToken, clearToken, type SessionToken } from "./tokenStore";
import { getBasePath } from "../api/basePath";

interface SessionTokenApiResponse {
  accessToken: string;
  expiresIn: number;
  tokenType: string;
  user: {
    id: number;
    username: string;
    name: string;
    lastname: string;
    organizations: { id: number; name: string }[];
  };
}

/**
 * Exchanges the current JSF session (cookie-based) for a short-lived JWT usable against /api/v1/**.
 *
 * `csrfToken` must be the Spring Security CSRF token value for the current JSF session (the host page
 * reads it from its own request attributes and passes it in at mount time — see mount.ts) since the
 * JSF security chain keeps CSRF protection enabled, unlike the stateless /api/v1 chain.
 */
export async function fetchSessionToken(csrfToken: string): Promise<SessionTokenApiResponse> {
  const response = await fetch(`${getBasePath()}/api/auth/session-token`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "X-CSRF-TOKEN": csrfToken,
    },
  });

  if (!response.ok) {
    clearToken();
    throw new Error(`session-token request failed with status ${response.status}`);
  }

  const body = (await response.json()) as SessionTokenApiResponse;
  const token: SessionToken = {
    accessToken: body.accessToken,
    tokenType: body.tokenType,
    expiresAt: Date.now() + body.expiresIn * 1000,
  };
  setToken(token);
  return body;
}
