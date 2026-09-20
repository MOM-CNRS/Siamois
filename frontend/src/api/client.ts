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
    const message = await response.text().catch(() => response.statusText);
    throw new ApiError(response.status, message || response.statusText);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
