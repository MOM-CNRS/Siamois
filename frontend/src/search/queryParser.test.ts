import { describe, expect, it } from "vitest";
import { formatFilter, keywordCompletion, parseQuery } from "./queryParser";

describe("parseQuery", () => {
  it("returns plain text untouched", () => {
    expect(parseQuery("  mur   nord ")).toEqual({ text: "mur nord", filters: [] });
  });

  it("extracts a keyword filter and keeps the rest as text", () => {
    expect(parseQuery("projet:ABC mur")).toEqual({ text: "mur", filters: [{ key: "projet", value: "ABC" }] });
  });

  it("accepts the english alias and any case", () => {
    expect(parseQuery("Project:ABC").filters).toEqual([{ key: "projet", value: "ABC" }]);
  });

  it("keeps a quoted value with spaces together", () => {
    expect(parseQuery('projet:"fouille 2024" mur')).toEqual({
      text: "mur",
      filters: [{ key: "projet", value: "fouille 2024" }],
    });
  });

  it("leaves an unknown keyword, or one without a value, as text", () => {
    expect(parseQuery("12:30").text).toBe("12:30");
    expect(parseQuery("projet:").filters).toEqual([]);
    expect(parseQuery("projet:").text).toBe("projet:");
  });
});

describe("formatFilter", () => {
  it("quotes a value with spaces", () => {
    expect(formatFilter({ key: "projet", value: "a b" })).toBe('projet:"a b"');
    expect(formatFilter({ key: "projet", value: "ab" })).toBe("projet:ab");
  });
});

describe("keywordCompletion", () => {
  it("completes a half-typed keyword only", () => {
    expect(keywordCompletion("mur proj")?.key).toBe("projet");
    expect(keywordCompletion("projet")).toBeUndefined();
    expect(keywordCompletion("projet:x")).toBeUndefined();
    expect(keywordCompletion("")).toBeUndefined();
  });
});
