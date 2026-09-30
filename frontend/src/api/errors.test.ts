import { describe, expect, it } from "vitest";
import { ApiError } from "./client";
import { messageForError } from "./errors";

describe("messageForError", () => {
  it("keeps the server's message on a 400 and a 409", () => {
    expect(messageForError(new ApiError(400, "identifier est obligatoire"), "Échec")).toBe("identifier est obligatoire");
    expect(messageForError(new ApiError(409, "Identifiant déjà utilisé"), "Échec")).toBe("Identifiant déjà utilisé");
  });

  it("falls back to the action when the server said nothing", () => {
    expect(messageForError(new ApiError(400, ""), "Échec de la création")).toBe("Échec de la création");
    expect(messageForError("boom", "Échec de la création")).toBe("Échec de la création");
  });

  it("explains 403, 404 and 5xx without the server's technical text", () => {
    expect(messageForError(new ApiError(403, "Forbidden"), "Échec")).toMatch(/droit/);
    expect(messageForError(new ApiError(404, "Not Found"), "Échec")).toMatch(/n'existe plus/);
    expect(messageForError(new ApiError(503, "java.lang.NPE"), "Échec de la création")).toBe(
      "Échec de la création : erreur du serveur, réessayez dans un instant.",
    );
  });

  it("names an unreachable server", () => {
    expect(messageForError(new TypeError("Failed to fetch"), "Échec de la création")).toMatch(/injoignable/);
  });
});
