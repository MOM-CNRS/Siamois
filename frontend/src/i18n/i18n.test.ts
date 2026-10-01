import { afterEach, describe, expect, it } from "vitest";
import { en } from "./en";
import { fr } from "./fr";
import { getLocale, intlLocale, resolveLocale, setLocale, t, tn } from "./index";

afterEach(() => setLocale("fr"));

// {name} placeholders a message uses, sorted.
const placeholders = (message: string) => [...message.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

describe("catalogs", () => {
  it("define the same keys in both languages", () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(fr).sort());
  });

  it("use the same placeholders in both languages", () => {
    for (const key of Object.keys(fr) as (keyof typeof fr)[]) {
      expect(placeholders(en[key]), key).toEqual(placeholders(fr[key]));
    }
  });

  it("give every plural key both forms", () => {
    const bases = Object.keys(fr).filter((k) => k.endsWith("_other")).map((k) => k.slice(0, -"_other".length));
    for (const base of bases) expect(Object.keys(fr), base).toContain(`${base}_one`);
  });

  it("leave no message empty", () => {
    for (const lang of [fr, en]) for (const [key, message] of Object.entries(lang)) expect(message.trim(), key).not.toBe("");
  });
});

describe("t", () => {
  it("speaks French by default and switches to English", () => {
    expect(getLocale()).toBe("fr");
    expect(t("common.cancel")).toBe("Annuler");
    setLocale("en");
    expect(t("common.cancel")).toBe("Cancel");
    expect(intlLocale()).toBe("en-GB");
  });

  it("fills placeholders and leaves unknown ones visible", () => {
    expect(t("filter.remove", { label: "Nom" })).toBe("Retirer le filtre Nom");
    expect(t("filter.remove")).toBe("Retirer le filtre {label}");
  });
});

describe("tn", () => {
  it("picks the plural form by the language's rules", () => {
    expect(tn("dup.summarySuffix", 1, { unit: "UE" })).toBe(" nouvelle UE");
    expect(tn("dup.summarySuffix", 3, { unit: "UE" })).toBe(" nouvelles UE");
    setLocale("en");
    expect(tn("dup.summarySuffix", 1, { unit: "RU" })).toBe(" new RU");
    expect(tn("dup.summarySuffix", 3, { unit: "RU" })).toBe(" new RUs");
  });
});

describe("resolveLocale", () => {
  it("keeps the language part and defaults to French", () => {
    expect(resolveLocale("en-GB")).toBe("en");
    expect(resolveLocale("EN")).toBe("en");
    expect(resolveLocale("fr")).toBe("fr");
    expect(resolveLocale("de")).toBe("fr");
    expect(resolveLocale(undefined)).toBe("fr");
  });
});
