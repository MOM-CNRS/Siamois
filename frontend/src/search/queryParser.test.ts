import {describe, expect, it} from "vitest";
import {formatFilter, keywordCompletion, parseQuery, tailKeyword} from "./queryParser";

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

describe("tailKeyword", () => {
  it("finds the keyword being typed at the end of the input", () => {
    expect(tailKeyword("projet:ab")).toEqual({ start: 0, key: "projet", partial: "ab" });
    expect(tailKeyword("mur Project:ab")).toEqual({ start: 4, key: "projet", partial: "ab" });
    expect(tailKeyword("projet:")).toEqual({ start: 0, key: "projet", partial: "" });
  });

  it("follows a quoted value until its quote closes", () => {
    expect(tailKeyword('projet:"fouille 20')).toEqual({ start: 0, key: "projet", partial: "fouille 20" });
    expect(tailKeyword('projet:"fouille 2024" ')).toBeUndefined();
  });

  it("ignores a finished keyword, an unknown one, and plain text", () => {
    expect(tailKeyword("projet:ab ")).toBeUndefined();
    expect(tailKeyword("12:30")).toBeUndefined();
    expect(tailKeyword("mur")).toBeUndefined();
  });
});

describe("custom keywords", () => {
  const keywords = [{ key: "type", names: ["type", "typologie"] }];

  it("recognises only the keywords it is given", () => {
    expect(parseQuery("type:mur projet:ABC", keywords)).toEqual({
      text: "projet:ABC",
      filters: [{ key: "type", value: "mur" }],
    });
    expect(parseQuery("type:mur").filters).toEqual([]);
  });

  it("completes and tails the given keywords", () => {
    expect(keywordCompletion("typo", keywords)?.key).toBe("type");
    expect(tailKeyword("mur type:fo", keywords)?.partial).toBe("fo");
    expect(tailKeyword("mur type:fo")).toBeUndefined();
  });
});
