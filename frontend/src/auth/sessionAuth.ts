import { apiUrl } from "../api/basePath";
import { setToken, clearToken, getValidToken } from "./tokenStore";

// Bridges the JSF session (already authenticated, cookie-based) into a JWT the stateless
// /api/v1/** chain accepts, by calling the generic POST /api/auth/session-token endpoint
// (fr.siamois.ui.api.SessionAuthController). That endpoint sits on the *session* filter chain,
// so this call needs the CSRF token JSF already renders into the page (${_csrf.token} /
// ${_csrf.headerName}) — passed in once at mount time, not read from the DOM here, since the
// mount div doesn't have to be a JSF-rendered form.
export interface CsrfConfig {
  headerName: string;
  token: string;
}

interface SessionTokenResponse {
  accessToken: string;
  expiresIn: number;
  tokenType: string;
}

let csrf: CsrfConfig | null = null;
let inFlight: Promise<string> | null = null;

export function configureCsrf(config: CsrfConfig): void {
  csrf = config;
}

async function fetchSessionToken(): Promise<string> {
  if (!csrf) {
    throw new Error("configureCsrf() must be called before requesting a session token");
  }
  const response = await fetch(apiUrl("/api/auth/session-token"), {
    method: "POST",
    credentials: "same-origin",
    headers: {
      [csrf.headerName]: csrf.token,
    },
  });
  if (!response.ok) {
    throw new Error(`Session token bridge failed: ${response.status}`);
  }
  const body: SessionTokenResponse = await response.json();
  setToken(body.accessToken, body.expiresIn);
  return body.accessToken;
}

/**
 * Returns a currently-valid access token, minting a fresh one from the JSF session if none is
 * cached or the cached one is close to expiry. Concurrent callers share one in-flight request.
 */
export async function getAccessToken(): Promise<string> {
  const cached = getValidToken();
  if (cached) return cached;

  if (!inFlight) {
    inFlight = fetchSessionToken().finally(() => {
      inFlight = null;
    });
  }
  return inFlight;
}

export function resetSession(): void {
  clearToken();
}
