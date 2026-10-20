import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { configureBasePath } from "../api/basePath";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/types";
import type { MountOptions } from "../mountOptions";
import { listPath, resolveEntityPath, viewIdOfPath } from "./paths";
import { decodeUrl, encodeUrl, initialNavState, navReducer } from "./reducer";
import { encodeFocusUrl } from "./url";

// A list opens on a saved view through its address: `/recording-unit?viewId=12`.

const fake = (key: string, segment: string) =>
  ({
    key,
    routes: { list: `/${segment}`, detail: (id: string | number) => `/${segment}/${id}` },
  }) as unknown as EntityTypeConfig<unknown, unknown>;

const options: MountOptions = {
  panelKind: "list",
  entityType: "recordingUnit",
  basePath: "/siamois",
  csrf: { headerName: "X", token: "t" },
  main: { resourceUri: "/recording-unit", title: "UE" },
};

const at = (url: string) => {
  const { pathname, search } = new URL(url, "http://localhost");
  return { pathname, search };
};

beforeAll(() => {
  registerEntityType(fake("project", "action-unit"));
  registerEntityType(fake("recordingUnit", "recording-unit"));
});
beforeEach(() => configureBasePath("/siamois"));

describe("viewIdOfPath", () => {
  it("reads a numeric viewId, wherever it is in the query", () => {
    expect(viewIdOfPath("/recording-unit?viewId=12")).toBe(12);
    expect(viewIdOfPath("/recording-unit?tab=x&viewId=5")).toBe(5);
  });

  it("reads nothing from a path without one, or with one that isn't a view id", () => {
    expect(viewIdOfPath("/recording-unit")).toBeUndefined();
    expect(viewIdOfPath("/recording-unit?viewId=abc")).toBeUndefined();
    expect(viewIdOfPath("/recording-unit?viewId=0")).toBeUndefined();
    expect(viewIdOfPath("/recording-unit?viewId=1.5")).toBeUndefined();
  });
});

describe("listPath", () => {
  it("is the list's own path, on a view when given one", () => {
    expect(listPath("recordingUnit")).toBe("/recording-unit");
    expect(listPath("recordingUnit", 12)).toBe("/recording-unit?viewId=12");
    expect(listPath("unknown", 12)).toBeUndefined();
  });
});

describe("resolveEntityPath", () => {
  it("names the view of a list, and none for a fiche", () => {
    expect(resolveEntityPath("/recording-unit?viewId=12")).toEqual({ entityType: "recordingUnit", viewId: 12 });
    expect(resolveEntityPath("/recording-unit")).toEqual({ entityType: "recordingUnit" });
    expect(resolveEntityPath("/recording-unit/3?viewId=12")).toEqual({ entityType: "recordingUnit", entityId: "3" });
  });
});

describe("a list on a view in the navigation state", () => {
  it("starts from the view the page was mounted on", () => {
    const state = initialNavState({ ...options, viewId: 12, main: { resourceUri: "/recording-unit?viewId=12", title: "UE" } });
    expect(state.view).toMatchObject({ panelKind: "list", entityType: "recordingUnit", viewId: 12 });
    expect(state.mainPath).toBe("/recording-unit?viewId=12");
  });

  it("puts the view in the address, and an F5 or Back reads it back", () => {
    const start = initialNavState(options);
    const state = navReducer(start, {
      type: "navigate",
      path: "/recording-unit?viewId=12",
      view: { panelKind: "list", entityType: "recordingUnit", viewId: 12 },
    });

    const restored = decodeUrl(at(encodeUrl(state)), start);

    expect(restored).toMatchObject({
      type: "restore",
      state: { mainPath: "/recording-unit?viewId=12", view: { panelKind: "list", entityType: "recordingUnit", viewId: 12 } },
    });
  });

  it("restores the default list from a path with no view", () => {
    const start = initialNavState({ ...options, viewId: 12, main: { resourceUri: "/recording-unit?viewId=12", title: "UE" } });
    const restored = decodeUrl(at(encodeFocusUrl({ mainPath: "/recording-unit" })), start);
    expect(restored).toMatchObject({ state: { view: { entityType: "recordingUnit" } } });
    expect(restored?.type === "restore" && restored.state.view.viewId).toBeFalsy();
  });
});
