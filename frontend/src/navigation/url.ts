import { apiUrl } from "../api/basePath";

// Base64url, no padding: exactly Java's Base64.getUrlEncoder().withoutPadding(), which is what
// FlowBean.redirectToFocus and FocusViewBean's own decode use for the `/focus/<main>?s=<overview>`
// URL scheme. UTF-8 in, like Java's String.getBytes.
function base64url(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64url(token: string): string {
  const padded = token.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(token.length / 4) * 4, "=");
  const binary = atob(padded);
  return new TextDecoder().decode(Uint8Array.from(binary, (c) => c.charCodeAt(0)));
}

/** What an address-bar URL says about the panes: the paths (`/action-unit/12`) and focus mode's way back. */
export interface FocusUrlParts {
  mainPath: string;
  overviewPath?: string;
  backUrl?: string;
}

/**
 * Every URL this app pushes is the canonical `/focus/<main>[?s=<overview>][&back=<back>]` form that
 * FocusViewBean decodes for every entity type, never a bare entity route: the address bar is what an
 * F5 replays, and a bare route either has no Spring controller or lands on a template without the
 * React branch. `back` is focus mode's way back (an absolute, context-path-prefixed URL, so a focus
 * URL can nest inside another's `back=`).
 */
export function encodeFocusUrl({ mainPath, overviewPath, backUrl }: FocusUrlParts): string {
  const params: string[] = [];
  if (overviewPath) params.push(`s=${base64url(overviewPath)}`);
  if (backUrl) params.push(`back=${base64url(backUrl)}`);
  const main = apiUrl(`/focus/${base64url(mainPath)}`);
  return params.length ? `${main}?${params.join("&")}` : main;
}

/** The inverse of encodeFocusUrl; null for any address that isn't a focus URL (or is malformed). */
export function decodeFocusUrl(location: { pathname: string; search: string }): FocusUrlParts | null {
  const prefix = apiUrl("/focus/");
  if (!location.pathname.startsWith(prefix)) return null;
  const mainToken = location.pathname.slice(prefix.length);
  if (!mainToken || mainToken.includes("/")) return null;
  try {
    const params = new URLSearchParams(location.search);
    const overviewToken = params.get("s");
    const backToken = params.get("back");
    return {
      mainPath: fromBase64url(mainToken),
      overviewPath: overviewToken ? fromBase64url(overviewToken) : undefined,
      backUrl: backToken ? fromBase64url(backToken) : undefined,
    };
  } catch {
    return null;
  }
}
