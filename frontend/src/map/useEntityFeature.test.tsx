import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {getEntityType} from "../entities/registry";
import {fetchMapFeature, type MapFeature} from "../entities/shared/listApi";
import {queryKeys} from "../api/queryKeys";
import {isMappable, MAP_STALE_TIME, useEntityFeature} from "./useEntityFeature";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../entities/registry", () => ({ getEntityType: vi.fn() }));
vi.mock("../entities/shared/listApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../entities/shared/listApi")>()),
  fetchMapFeature: vi.fn(),
}));

const feature: MapFeature = { id: "12", label: "UE-12", geometry: { type: "Point", coordinates: [2, 48] } };

// The registry, reduced to what the hook reads: the collection path and whether the type is mappable.
function registerTypes() {
  vi.mocked(getEntityType).mockReset().mockImplementation(((key: string) => {
    if (key === "recordingUnit") return { collectionPath: "recording-units", list: { mappable: true } };
    if (key === "project") return { collectionPath: "projects", list: { mappable: false } };
    if (key === "phase") return { collectionPath: "phases", list: {} };
    return undefined;
  }) as typeof getEntityType);
}

type Result = ReturnType<typeof useEntityFeature>;
type Args = { entityType: string; id: string | number | undefined; enabled?: boolean; geometry?: string };

let latest: Result;
let root: Root | undefined;
let client: QueryClient;

function Probe({ entityType, id, enabled, geometry }: Readonly<Args>) {
  latest = useEntityFeature(entityType, id, enabled, geometry);
  return null;
}

function render(args: Args) {
  act(() =>
    root!.render(
      <QueryClientProvider client={client}>
        <Probe {...args} />
      </QueryClientProvider>,
    ),
  );
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(() => {
  registerTypes();
  vi.mocked(fetchMapFeature).mockReset().mockResolvedValue(feature);
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  root = createRoot(document.createElement("div"));
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
  client.clear();
});

describe("useEntityFeature", () => {
  it("fetches a mappable entity's geometry from its collection", async () => {
    render({ entityType: "recordingUnit", id: 12 });
    expect(latest).toEqual({ feature: undefined, isLoading: true });

    await flush();

    expect(fetchMapFeature).toHaveBeenCalledWith("recording-units", 12, undefined);
    expect(latest).toEqual({ feature, isLoading: false });
  });

  it("asks for an additional geometry column and caches it under the entity's detail", async () => {
    render({ entityType: "recordingUnit", id: "12", geometry: "-9" });
    await flush();

    expect(fetchMapFeature).toHaveBeenCalledWith("recording-units", "12", "-9");
    expect(client.getQueryData(queryKeys.entityMapFeature("recordingUnit", "12", "-9"))).toBe(feature);
  });

  it("keeps the geometry fresh for a minute", async () => {
    render({ entityType: "recordingUnit", id: 12 });
    await flush();

    const query = client.getQueryCache().find({ queryKey: queryKeys.entityMapFeature("recordingUnit", 12) });
    expect((query?.options as { staleTime?: number }).staleTime).toBe(MAP_STALE_TIME);
    expect(MAP_STALE_TIME).toBe(60_000);
  });

  it("is null when the entity has no placeable geometry", async () => {
    vi.mocked(fetchMapFeature).mockResolvedValueOnce(null);

    render({ entityType: "recordingUnit", id: 12 });
    await flush();

    expect(latest.feature).toBeNull();
  });

  it("fetches nothing while disabled", async () => {
    render({ entityType: "recordingUnit", id: 12, enabled: false });
    await flush();

    expect(fetchMapFeature).not.toHaveBeenCalled();
    expect(latest.feature).toBeUndefined();
  });

  it("fetches nothing without an id", async () => {
    render({ entityType: "recordingUnit", id: undefined });
    await flush();

    expect(fetchMapFeature).not.toHaveBeenCalled();
  });

  it("fetches a zero id, which is an id", async () => {
    render({ entityType: "recordingUnit", id: 0 });
    await flush();

    expect(fetchMapFeature).toHaveBeenCalledWith("recording-units", 0, undefined);
  });

  it("fetches nothing for a type that cannot be placed on a map", async () => {
    render({ entityType: "project", id: 3 });
    await flush();

    expect(fetchMapFeature).not.toHaveBeenCalled();
  });

  it("fetches nothing for an unknown type", async () => {
    render({ entityType: "nope", id: 3 });
    await flush();

    expect(fetchMapFeature).not.toHaveBeenCalled();
    expect(latest.feature).toBeUndefined();
  });

  it("leaves the feature unknown when the request fails", async () => {
    vi.mocked(fetchMapFeature).mockRejectedValueOnce(new Error("boom"));

    render({ entityType: "recordingUnit", id: 12 });
    await flush();

    expect(latest).toEqual({ feature: undefined, isLoading: false });
  });
});

describe("isMappable", () => {
  beforeEach(registerTypes);

  it("is true for a type whose list is mappable", () => {
    expect(isMappable("recordingUnit")).toBe(true);
  });

  it("is false for a type whose list says it is not", () => {
    expect(isMappable("project")).toBe(false);
  });

  it("is false for a type that says nothing", () => {
    expect(isMappable("phase")).toBe(false);
  });

  it("is false for an unknown type", () => {
    expect(isMappable("nope")).toBe(false);
  });

  it("is false without a type", () => {
    expect(isMappable(undefined)).toBe(false);
    expect(getEntityType).not.toHaveBeenCalled();
  });
});
