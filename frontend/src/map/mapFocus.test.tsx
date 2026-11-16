import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, describe, expect, it} from "vitest";
import {focusOnMap, type MapFocusTarget, useMapFocusTarget} from "./mapFocus";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | undefined;

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
});

function Target({ target }: { target: MapFocusTarget }) {
  useMapFocusTarget(target);
  return null;
}

function mount(target: MapFocusTarget) {
  root = createRoot(document.createElement("div"));
  act(() => root!.render(<Target target={target} />));
}

describe("focusOnMap", () => {
  it("is answered by a mounted map that shows the entity", () => {
    mount((type, id) => type === "recordingUnit" && id === "12");
    expect(focusOnMap("recordingUnit", "12")).toBe(true);
    expect(focusOnMap("recordingUnit", "13")).toBe(false);
  });

  it("is answered by no map once it is unmounted", () => {
    mount(() => true);
    act(() => root!.unmount());
    root = undefined;
    expect(focusOnMap("recordingUnit", "12")).toBe(false);
  });
});
