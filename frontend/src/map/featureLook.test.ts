import {describe, expect, it} from "vitest";
import type {MapFeature} from "../entities/shared/listApi";
import {featureOptions, LIST_LOOK, SELECTED_LOOK, typeColor} from "./featureLook";

describe("typeColor", () => {
  it("gives a type the same colour every time, and different types different ones", () => {
    expect(typeColor("Couche")).toBe(typeColor("Couche"));
    expect(typeColor("Couche")).not.toBe(typeColor("Fosse"));
  });

  it("greys out a unit with no type", () => {
    expect(typeColor(null)).toBe("#6c757d");
  });
});

describe("featureOptions", () => {
  const feature: MapFeature = { id: "1", label: "UE-1", type: { resolvedLabel: "Couche" }, geometry: { type: "Point", coordinates: [2, 48] } };

  it("paints a feature in its type's colour, with the look's strokes", () => {
    const options = featureOptions(feature, LIST_LOOK);
    expect(options).toMatchObject({ color: typeColor("Couche"), fillColor: typeColor("Couche"), weight: 2, radius: 6, interactive: true });
    expect(options.className).toBeUndefined();
  });

  it("draws the selection as a non-interactive overlay the theme outlines", () => {
    expect(featureOptions(feature, SELECTED_LOOK)).toMatchObject({ weight: 4, interactive: false, className: "entity-map-selected" });
  });
});
