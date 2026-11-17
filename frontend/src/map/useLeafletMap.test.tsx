import {act, createRef, type RefObject, useRef} from "react";
import {createRoot, type Root} from "react-dom/client";
import type L from "leaflet";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {createBaseMap} from "./leafletMap";
import {useLeafletMap} from "./useLeafletMap";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./leafletMap", () => ({ createBaseMap: vi.fn() }));

const maps: { remove: ReturnType<typeof vi.fn>; invalidateSize: ReturnType<typeof vi.fn>; container: HTMLElement }[] = [];
let latest: L.Map | null;
let renders: (L.Map | null)[];
let root: Root | undefined;

// The map's container is the probe's own div, attached before effects run.
function WithContainer() {
  const ref = useRef<HTMLDivElement>(null);
  latest = useLeafletMap(ref as RefObject<HTMLElement>);
  renders.push(latest);
  return <div ref={ref} className="map-container" />;
}

// A ref that never gets an element (the container is not rendered).
function WithoutContainer({ containerRef }: Readonly<{ containerRef: RefObject<HTMLElement> }>) {
  latest = useLeafletMap(containerRef);
  renders.push(latest);
  return null;
}

beforeEach(() => {
  maps.length = 0;
  renders = [];
  vi.mocked(createBaseMap).mockReset().mockImplementation((container) => {
    const map = { remove: vi.fn(), invalidateSize: vi.fn(), container };
    maps.push(map);
    return map as unknown as L.Map;
  });
  root = createRoot(document.createElement("div"));
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
});

describe("useLeafletMap", () => {
  it("is null on the first render, then the map created in the container", () => {
    act(() => root!.render(<WithContainer />));

    expect(renders[0]).toBeNull();
    expect(createBaseMap).toHaveBeenCalledTimes(1);
    expect(maps[0].container.classList.contains("map-container")).toBe(true);
    expect(latest).toBe(maps[0]);
  });

  it("creates the map once, whatever the re-renders", () => {
    act(() => root!.render(<WithContainer />));
    act(() => root!.render(<WithContainer />));

    expect(createBaseMap).toHaveBeenCalledTimes(1);
    expect(latest).toBe(maps[0]);
  });

  it("creates no map while the container is not rendered", () => {
    act(() => root!.render(<WithoutContainer containerRef={createRef<HTMLElement>() as RefObject<HTMLElement>} />));

    expect(createBaseMap).not.toHaveBeenCalled();
    expect(latest).toBeNull();
  });

  it("removes the map on unmount", () => {
    act(() => root!.render(<WithContainer />));

    act(() => root!.unmount());
    root = undefined;

    expect(maps[0].remove).toHaveBeenCalledTimes(1);
  });

  it("does not remove a map it never created", () => {
    act(() => root!.render(<WithoutContainer containerRef={createRef<HTMLElement>() as RefObject<HTMLElement>} />));

    act(() => root!.unmount());
    root = undefined;

    expect(maps).toHaveLength(0);
  });

  it("lets a failure to build the map through", () => {
    vi.mocked(createBaseMap).mockImplementationOnce(() => {
      throw new Error("Map container is already initialized.");
    });
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    try {
      expect(() => act(() => root!.render(<WithContainer />))).toThrow("Map container is already initialized.");
    } finally {
      consoleError.mockRestore();
    }
  });

  describe("following the container's size", () => {
    let observers: { callback: () => void; observed: Element[]; disconnect: () => void }[];

    beforeEach(() => {
      observers = [];
      vi.stubGlobal(
        "ResizeObserver",
        class {
          private readonly entry: (typeof observers)[number];
          constructor(callback: () => void) {
            this.entry = { callback, observed: [], disconnect: vi.fn() };
            observers.push(this.entry);
          }
          observe(element: Element) {
            this.entry.observed.push(element);
          }
          disconnect() {
            this.entry.disconnect();
          }
        },
      );
    });

    afterEach(() => vi.unstubAllGlobals());

    it("tells the map when its container is resized (the overview opening beside it)", () => {
      act(() => root!.render(<WithContainer />));

      expect(observers[0].observed).toEqual([maps[0].container]);
      observers[0].callback();
      expect(maps[0].invalidateSize).toHaveBeenCalledTimes(1);
    });

    it("stops watching the container on unmount", () => {
      act(() => root!.render(<WithContainer />));

      act(() => root!.unmount());
      root = undefined;

      expect(observers[0].disconnect).toHaveBeenCalledTimes(1);
    });
  });
});
