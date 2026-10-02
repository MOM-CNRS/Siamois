import { describe, expect, it } from "vitest";
import { typeColor } from "./EntityMapView";

describe("typeColor", () => {
  it("gives a type the same colour every time, and different types different ones", () => {
    expect(typeColor("Couche")).toBe(typeColor("Couche"));
    expect(typeColor("Couche")).not.toBe(typeColor("Fosse"));
  });

  it("greys out a unit with no type", () => {
    expect(typeColor(null)).toBe("#6c757d");
  });
});
