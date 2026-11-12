import {describe, expect, it} from "vitest";
import type {EntityTypeConfig} from "../../entities/shared/types";
import {pinnedColumnsOf, VALIDATION_STATUS_COLUMN_KEY, validationStatusColumn} from "./validationStatusColumn";

const configWith = (columns: { key: string; identifier?: boolean }[]) =>
  ({
    key: "find",
    collectionPath: "finds",
    list: { columns: columns.map((c) => ({ header: c.key, render: () => null, ...c })) },
  }) as unknown as EntityTypeConfig<unknown, unknown>;

describe("pinnedColumnsOf", () => {
  it("puts the status first, before the identifier", () => {
    const keys = pinnedColumnsOf(configWith([{ key: "id", identifier: true }, { key: "type" }])).map((c) => c.key);
    expect(keys).toEqual([VALIDATION_STATUS_COLUMN_KEY, "id", "type"]);
  });

  it("has no column without a config", () => {
    expect(pinnedColumnsOf(undefined)).toEqual([]);
  });
});

describe("validationStatusColumn", () => {
  it("sorts, and filters by the four statuses in workflow order", () => {
    const column = validationStatusColumn(configWith([]));
    expect(column.sortable).toBe(true);
    expect(column.filterOptions?.map((o) => o.id)).toEqual(["INCOMPLETE", "COMPLETE", "VALIDATED", "CANCELLED"]);
  });
});
