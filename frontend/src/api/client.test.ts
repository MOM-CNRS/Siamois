import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiBlob, apiFetch, ApiError } from "./client";
import { setLocale } from "../i18n";

vi.mock("../auth/sessionAuth", () => ({
  getAccessToken: vi.fn().mockResolvedValue("test-token"),
  resetSession: vi.fn(),
}));

// extractErrorMessage (client.ts) surfaces OpenApiRestExceptionHandler's {error, message} body
// instead of the raw response text — the identifier inline-edit error in the Project fiche
// (FicheTab.tsx) depends on ApiError.message actually being the human-readable string.
describe("apiFetch error handling", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(),
    );
  });

  it("extracts the message field from a JSON error body", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ error: "bad_request", message: "identifier est obligatoire" }), {
        status: 400,
        statusText: "Bad Request",
      }),
    );

    await expect(apiFetch("/api/v1/projects/5")).rejects.toMatchObject(
      new ApiError(400, "identifier est obligatoire"),
    );
  });

  it("falls back to the raw text when the error body isn't JSON", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response("plain text failure", { status: 500 }));

    await expect(apiFetch("/api/v1/projects/5")).rejects.toMatchObject(new ApiError(500, "plain text failure"));
  });
});

// BookmarkControllerApi.create answers 201 Created with no body — response.json() throws a
// SyntaxError on an empty string, which apiFetch used to call unconditionally on any non-204
// success.
describe("apiFetch success handling", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("resolves to undefined for a successful response with no body", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response("", { status: 201 }));

    await expect(apiFetch("/api/v1/bookmarks")).resolves.toBeUndefined();
  });

  it("still parses JSON for a normal successful response", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }));

    await expect(apiFetch("/api/v1/projects/5")).resolves.toEqual({ ok: true });
  });
});

// The JSF session cookie must never ride along on /api/v1 calls: the API chain used to rotate the
// JSF session id on each of them, and parallel calls raced the new cookies until the user was
// logged out (see WebSecurityConfig#apiV1SecurityFilterChain).
describe("apiFetch credentials", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("sends the bearer token but never the session cookie", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200 }));

    await apiFetch("/api/v1/recording-units/94");

    const init = vi.mocked(fetch).mock.calls[0][1]!;
    expect(init.credentials).toBe("omit");
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer test-token");
  });
});

describe("apiFetch language", () => {
  it("asks for the labels in the page language, not the browser's", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 200 })));
    setLocale("en");
    try {
      await apiFetch("/api/v1/projects");
    } finally {
      setLocale("fr");
    }
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers["Accept-Language"]).toBe("en");
  });
});

describe("apiFetch bodies", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("sends a FormData as is, without forcing a JSON content type", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
    const body = new FormData();
    body.append("file", new File(["a"], "a.txt"));

    await apiFetch("/api/v1/documents/1/file", { method: "PUT", body });

    const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
    expect(init.body).toBe(body);
    expect((init.headers as Record<string, string>)["Content-Type"]).toBeUndefined();
  });

  it("still sends a plain object as JSON", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));

    await apiFetch("/api/v1/x", { method: "POST", body: { a: 1 } });

    const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
    expect(init.body).toBe('{"a":1}');
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });
});

describe("apiBlob", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("fetches the bytes with the bearer token", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response("abc", { status: 200 }));

    const blob = await apiBlob("/api/v1/documents/1/file");

    expect(blob.size).toBe(3);
    const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer test-token");
  });

  it("retries once after a 401, then reports the server's error", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: "Fichier introuvable" }), { status: 404 }));

    await expect(apiBlob("/api/v1/documents/1/file")).rejects.toMatchObject(new ApiError(404, "Fichier introuvable"));
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});
