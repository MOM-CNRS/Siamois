import { apiUrl } from "./basePath";
import { getAccessToken, resetSession } from "../auth/sessionAuth";

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
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
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401 && allowRetry) {
    resetSession();
    return doFetch<T>(path, options, /* allowRetry */ false);
  }

  if (!response.ok) {
    throw new ApiError(response.status, await extractErrorMessage(response));
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

// OpenApiRestExceptionHandler always answers errors as JSON {error, message} — surface that
// human-readable message rather than the raw response body (previously shown verbatim, e.g. to
// the identifier inline-edit error in the Project fiche).
async function extractErrorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => "");
  if (!text) return response.statusText;
  try {
    const body = JSON.parse(text) as { message?: string };
    return body.message || response.statusText;
  } catch {
    return text;
  }
}
