import {describe, expect, it} from "vitest";
import {filterOptionsOffline} from "./offline";

describe("filterOptionsOffline, list following the type of the record", () => {
  const options = [{ id: "1" }, { id: "2" }, { id: "3" }];

  it("keeps the values the type is related to", () => {
    expect(filterOptionsOffline(options, { kind: "RELATED_TO_TYPE", relatedTo: "9" }, { "9": ["2", "3"] }))
      .toEqual([{ id: "2" }, { id: "3" }]);
  });

  it("keeps the whole list when the type is related to none of them", () => {
    expect(filterOptionsOffline(options, { kind: "RELATED_TO_TYPE", relatedTo: "9" }, { "9": ["40"] })).toEqual(options);
    expect(filterOptionsOffline(options, { kind: "RELATED_TO_TYPE", relatedTo: null }, {})).toEqual(options);
  });
});
