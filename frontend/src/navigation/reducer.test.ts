import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { configureBasePath } from "../api/basePath";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/types";
import type { MountOptions } from "../mountOptions";
import { decodeUrl, encodeUrl, initialNavState, navReducer, serverSync, transitionOf, type NavAction, type NavState } from "./reducer";
import { encodeFocusUrl } from "./url";

function fake(key: string, segment: string): EntityTypeConfig<unknown, unknown> {
  return {
    key,
    routes: { list: `/${segment}`, detail: (id: string | number) => `/${segment}/${id}` },
  } as unknown as EntityTypeConfig<unknown, unknown>;
}

const options: MountOptions = {
  panelKind: "list",
  entityType: "project",
  basePath: "/siamois",
  csrf: { headerName: "X", token: "t" },
  main: { resourceUri: "/action-unit", title: "Projets" },
};

const at = (url: string) => {
  const { pathname, search } = new URL(url, "http://localhost");
  return { pathname, search };
};
const apply = (state: NavState, ...actions: NavAction[]) => actions.reduce(navReducer, state);
const detail = (entityType: string, entityId: string): NavAction => ({
  type: "navigate",
  path: entityType === "project" ? `/action-unit/${entityId}` : `/recording-unit/${entityId}`,
  view: { panelKind: "detail", entityType, entityId },
});

beforeAll(() => {
  registerEntityType(fake("project", "action-unit"));
  registerEntityType(fake("recordingUnit", "recording-unit"));
});
beforeEach(() => configureBasePath("/siamois"));

describe("navReducer", () => {
  const start = initialNavState(options);

  it("starts from the mount options, without pushing anything", () => {
    expect(start).toMatchObject({ mainPath: "/action-unit", overview: null, navigatedAway: false, urlOwner: "browser", focusStack: [] });
  });

  it("navigates: new main, focus mode over", () => {
    const focused = apply(initialNavState({ ...options, goBackUrl: "/back" }), detail("project", "1"));
    expect(focused).toMatchObject({ mainPath: "/action-unit/1", navigatedAway: true, urlOwner: "app", backUrl: undefined, focusStack: [] });
    expect(focused.view).toMatchObject({ panelKind: "detail", entityId: "1" });
  });

  it("opens, retargets and closes the overview without touching the main pane", () => {
    const opened = apply(start, { type: "openOverview", overview: { entityType: "project", entityId: "1" } });
    expect(opened.overview).toEqual({ entityType: "project", entityId: "1" });
    expect(opened.view).toBe(start.view);
    const closed = apply(opened, { type: "closeOverview" });
    expect(closed.overview).toBeNull();
  });

  it("promotes the overview to main, then puts everything back", () => {
    const withOverview = apply(start, { type: "openOverview", overview: { entityType: "recordingUnit", entityId: "3" } });
    const focused = apply(withOverview, { type: "enterFocus" });
    expect(focused).toMatchObject({ mainPath: "/recording-unit/3", overview: null, navigatedAway: true });
    expect(focused.view).toMatchObject({ panelKind: "detail", entityType: "recordingUnit", entityId: "3" });
    // The way back is the very URL that was left.
    expect(focused.backUrl).toBe(encodeUrl(withOverview));
    expect(focused.focusStack).toHaveLength(1);

    const back = apply(focused, { type: "closeFocus" });
    expect(back).toMatchObject({ mainPath: "/action-unit", navigatedAway: false, backUrl: undefined, focusStack: [] });
    expect(back.overview).toEqual({ entityType: "recordingUnit", entityId: "3" });
    expect(encodeUrl(back)).toBe(encodeUrl(withOverview));
  });

  it("nests focus levels", () => {
    const one = apply(start, { type: "openOverview", overview: { entityType: "project", entityId: "1" } }, { type: "enterFocus" });
    const two = apply(one, { type: "openOverview", overview: { entityType: "recordingUnit", entityId: "3" } }, { type: "enterFocus" });
    expect(two.focusStack).toHaveLength(2);
    const unwound = apply(two, { type: "closeFocus" }, { type: "closeFocus" });
    expect(unwound.mainPath).toBe("/action-unit");
    expect(unwound.overview).toMatchObject({ entityId: "1" });
  });

  it("ignores enterFocus without an overview and closeFocus without a level", () => {
    expect(apply(start, { type: "enterFocus" })).toBe(start);
    expect(apply(start, { type: "closeFocus" })).toBe(start);
  });

  it("transitions animate the opening only", () => {
    const overview = { entityType: "project", entityId: "1" };
    const opened = apply(start, { type: "openOverview", overview });
    expect(transitionOf(start, { type: "openOverview", overview })).toBe("overview-open");
    expect(transitionOf(opened, { type: "openOverview", overview })).toBeUndefined();
    expect(transitionOf(opened, { type: "closeOverview" })).toBe("overview-close");
    expect(transitionOf(opened, { type: "enterFocus" })).toBe("focus-enter");
    expect(transitionOf(start, { type: "enterFocus" })).toBeUndefined();
  });
});

describe("decodeUrl", () => {
  const start = initialNavState(options);
  const restoreOf = (url: string) => {
    const action = decodeUrl(at(url), start);
    return action?.type === "restore" ? action.state : null;
  };

  it("round-trips every state the app can push", () => {
    let state = start;
    const steps: NavAction[] = [
      detail("project", "1"),
      { type: "openOverview", overview: { entityType: "recordingUnit", entityId: "3" } },
      { type: "enterFocus" },
      { type: "closeFocus" },
      { type: "closeOverview" },
    ];
    for (const step of steps) {
      state = navReducer(state, step);
      const restored = restoreOf(encodeUrl(state));
      expect(restored, step.type).toMatchObject({
        mainPath: state.mainPath,
        backUrl: state.backUrl,
        view: { entityType: state.view.entityType, entityId: state.view.entityId == null ? undefined : String(state.view.entityId), panelKind: state.view.panelKind },
      });
      expect(restored?.overview?.entityId).toEqual(state.overview?.entityId);
      expect(encodeUrl({ ...state, ...restored! })).toBe(encodeUrl(state));
    }
  });

  it("gives back the mounted view for the path JSF mounted it with (Home has no route)", () => {
    const home = initialNavState({ ...options, panelKind: "home", main: { resourceUri: "/welcome", title: "Accueil" } });
    const action = decodeUrl(at(encodeFocusUrl({ mainPath: "/welcome" })), home);
    expect(action).toMatchObject({ type: "restore", state: { view: home.view, navigatedAway: false } });
  });

  it("marks a restored state as the browser's, with no focus stack", () => {
    const restored = navReducer({ ...start, focusStack: [{} as never] }, decodeUrl(at(encodeFocusUrl({ mainPath: "/action-unit/1" })), start)!);
    expect(restored).toMatchObject({ urlOwner: "browser", focusStack: [], navigatedAway: true });
  });

  it("is null for what it can't show, so the caller reloads", () => {
    expect(decodeUrl(at("/siamois/action-unit/1"), start)).toBeNull();
    expect(decodeUrl(at(encodeFocusUrl({ mainPath: "/unknown/1" })), start)).toBeNull();
    expect(decodeUrl(at(encodeFocusUrl({ mainPath: "/action-unit/1", overviewPath: "/action-unit" })), start)).toBeNull();
  });
});

describe("serverSync", () => {
  const overview = (id: string) => ({ entityType: "project", entityId: id });

  it("tells nothing when nothing moved", () => {
    expect(serverSync({ mainPath: "/a", overview: null }, { mainPath: "/a", overview: null })).toEqual({ closeOverview: false });
  });

  it("closes an overview before the main moves, and sets one after", () => {
    expect(serverSync({ mainPath: "/a", overview: overview("1") }, { mainPath: "/b", overview: null })).toMatchObject({
      closeOverview: true,
      setMain: "/b",
    });
    expect(serverSync({ mainPath: "/b", overview: null }, { mainPath: "/a", overview: overview("1") })).toMatchObject({
      closeOverview: false,
      setMain: "/a",
      setOverview: overview("1"),
    });
  });

  it("doesn't resend the overview the server already holds", () => {
    expect(serverSync({ mainPath: "/a", overview: overview("1") }, { mainPath: "/a", overview: overview("1") }).setOverview).toBeUndefined();
  });
});
