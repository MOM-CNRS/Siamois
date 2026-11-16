import {describe, expect, it} from "vitest";
import {placeRefsOf} from "./placeRefs";

describe("placeRefsOf", () => {
  it("reads the places of single and multiple place answers, in order, each once", () => {
    expect(placeRefsOf({
      "-304": { answerType: "SELECT_ONE_SPATIAL_UNIT", value: { resourceType: "places", id: "7", name: "Lyon" } },
      "-1": { answerType: "TEXT", value: "Couche" },
      "12": { answerType: "SELECT_MULTIPLE_SPATIAL_UNIT", value: { values: [{ resourceType: "places", id: 8 }, { resourceType: "places", id: "7" }] } },
    })).toEqual(["7", "8"]);
  });

  it("puts the entity's own single place before an added single place field", () => {
    expect(placeRefsOf({
      "15": { answerType: "SELECT_ONE_SPATIAL_UNIT", value: { resourceType: "places", id: "30" } },
      "-304": { answerType: "SELECT_ONE_SPATIAL_UNIT", value: { resourceType: "places", id: "7" } },
    })).toEqual(["7", "30"]);
  });

  it("names no place for empty or missing answers", () => {
    expect(placeRefsOf({ "-304": { answerType: "SELECT_ONE_SPATIAL_UNIT", value: null } })).toEqual([]);
    expect(placeRefsOf(undefined)).toEqual([]);
  });
});
