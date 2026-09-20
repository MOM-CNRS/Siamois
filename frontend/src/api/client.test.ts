import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiError } from "./client";

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
