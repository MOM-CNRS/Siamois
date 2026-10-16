import { apiUrl } from "./basePath";
import { getLocale } from "../i18n";
import { getAccessToken, resetSession } from "../auth/sessionAuth";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    // The parsed error body ({error, message}…) and the request path, for diagnostics and messages.
    public readonly body?: unknown,
    public readonly path?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
}

/**
 * Generic fetch wrapper for /api/v1/**: attaches the bridged bearer token, retries once on 401
 * (in case the token expired mid-flight and a fresh session-token mint fixes it), and throws
 * ApiError with the parsed status/message otherwise. Every entity's api.* implementation
 * (list/get/formConfig/...) goes through this — no entity-specific fetch logic.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return doFetch<T>(path, options, /* allowRetry */ true);
}

async function doFetch<T>(path: string, options: RequestOptions, allowRetry: boolean): Promise<T> {
  const token = await getAccessToken();
  const { body, headers, ...rest } = options;

  const response = await fetch(apiUrl(path), {
    // The bearer token is the only credential /api/v1 accepts; never send the JSF session cookie
    // along. Sending it let the API's security chain rotate the JSF session id on every call, and
    // parallel calls raced the new cookies until the user got logged out (WebSecurityConfig's
    // apiV1SecurityFilterChain has the server-side half of that fix).
    credentials: "omit",
    ...rest,
    headers: {
      Authorization: `Bearer ${token}`,
      // The labels the API resolves itself (field names, concepts, types) follow the page language,
      // not the browser's: the user picks it in SIAMOIS (LangBean).
      "Accept-Language": getLocale(),
      // A FormData body carries its own multipart Content-Type, boundary included.
      ...(body !== undefined && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
  });

  if (response.status === 401 && allowRetry) {
    resetSession();
    return doFetch<T>(path, options, /* allowRetry */ false);
  }

  if (!response.ok) {
    const { message, body } = await readError(response);
    throw new ApiError(response.status, message, body, path);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  // A successful response can still have no body (e.g. BookmarkControllerApi's 201 Created) —
  // response.json() throws a SyntaxError on an empty string, so check first rather than assuming
  // every non-204 success carries JSON.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// RestExceptionHandler always answers errors as JSON {error, message, details?} — surface that
// human-readable message rather than the raw response body (previously shown verbatim, e.g. to
// the identifier inline-edit error in the Project fiche).
async function readError(response: Response): Promise<{ message: string; body?: unknown }> {
  const text = await response.text().catch(() => "");
  if (!text) return { message: response.statusText };
  try {
    const body = JSON.parse(text) as { message?: string };
    return { message: body.message || response.statusText, body };
  } catch {
    return { message: text };
  }
}

/**
 * A binary response (a stored file). The API accepts only the bearer token, so a plain link cannot
 * download it: the bytes are fetched with the token and handed to the browser as an object URL.
 */
export async function apiBlob(path: string, allowRetry = true): Promise<Blob> {
  const token = await getAccessToken();
  const response = await fetch(apiUrl(path), {
    credentials: "omit",
    headers: { Authorization: `Bearer ${token}`, "Accept-Language": getLocale() },
  });
  if (response.status === 401 && allowRetry) {
    resetSession();
    return apiBlob(path, false);
  }
  if (!response.ok) {
    const { message, body } = await readError(response);
    throw new ApiError(response.status, message, body, path);
  }
  return response.blob();
}
