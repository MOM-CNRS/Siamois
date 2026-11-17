import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {fetchMap, type MapData} from "../../../entities/shared/listApi";
import type {ListParams} from "../../../entities/shared/types";
import {queryKeys} from "../../../api/queryKeys";
import {useEntityMapData} from "./useEntityMapData";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../../entities/shared/listApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../entities/shared/listApi")>()),
  fetchMap: vi.fn(),
}));

const mapData: MapData = { features: [], meta: { total: 0, examined: 0 } };

type Result = ReturnType<typeof useEntityMapData>;
type Args = { params: ListParams; enabled?: boolean; geometry?: string };

let latest: Result;
let root: Root | undefined;
let client: QueryClient;

function Probe({ params, enabled, geometry }: Readonly<Args>) {
  latest = useEntityMapData("recordingUnit", "recording-units", params, enabled, geometry);
  return null;
}

// Lets the query settle inside act(), so its state updates are flushed before asserting.
async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
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

beforeEach(() => {
  vi.mocked(fetchMap).mockReset().mockResolvedValue(mapData);
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  root = createRoot(document.createElement("div"));
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
  client.clear();
});

const base: ListParams = { organizationId: 3, search: "fosse", offset: 0, limit: 50, sort: "identifier,asc" };

describe("useEntityMapData", () => {
  it("fetches the map of the list's result set in the list's order, without its page", async () => {
    render({ params: base });

    await flush();
    expect(latest.data).toBe(mapData);
    expect(fetchMap).toHaveBeenCalledWith(
      "recording-units",
      { organizationId: 3, search: "fosse", filters: undefined, scope: undefined, sort: "identifier,asc" },
      undefined,
    );
  });

  it("caches under the list's map key", async () => {
    render({ params: base, geometry: "-4" });

    await flush();
    expect(latest.isSuccess).toBe(true);
    const request = { organizationId: 3, search: "fosse", filters: undefined, scope: undefined, sort: "identifier,asc" };
    expect(client.getQueryData(queryKeys.entityListMap("recordingUnit", request, "-4"))).toBe(mapData);
  });

  it("asks for an additional geometry column when one is chosen", async () => {
    render({ params: base, geometry: "-4" });

    await flush();
    expect(fetchMap).toHaveBeenCalled();
    expect(vi.mocked(fetchMap).mock.calls[0][2]).toBe("-4");
  });

  it("does not refetch when only the page of the list changes", async () => {
    render({ params: base });
    await flush();
    expect(latest.isSuccess).toBe(true);

    render({ params: { ...base, offset: 50, limit: 100 } });

    expect(latest.data).toBe(mapData);
    expect(fetchMap).toHaveBeenCalledTimes(1);
  });

  it("refetches when the order of the list changes, the features coming in it", async () => {
    render({ params: base });
    await flush();
    expect(latest.isSuccess).toBe(true);

    render({ params: { ...base, sort: "identifier,desc" } });

    await flush();
    expect(fetchMap).toHaveBeenCalledTimes(2);
  });

  it("refetches when what selects the rows changes", async () => {
    render({ params: base });
    await flush();
    expect(latest.isSuccess).toBe(true);

    render({ params: { ...base, filters: { "-1": { op: "in", v: ["4"] } } } });

    await flush();
    expect(fetchMap).toHaveBeenCalledTimes(2);
  });

  it("refetches when another geometry column is chosen", async () => {
    render({ params: base });
    await flush();
    expect(latest.isSuccess).toBe(true);

    render({ params: base, geometry: "-9" });

    await flush();
    expect(fetchMap).toHaveBeenCalledTimes(2);
    expect(vi.mocked(fetchMap).mock.calls[1][2]).toBe("-9");
  });

  it("fetches nothing while disabled", async () => {
    render({ params: base, enabled: false });

    await flush();
    expect(fetchMap).not.toHaveBeenCalled();
    expect(latest.data).toBeUndefined();
    expect(latest.fetchStatus).toBe("idle");
  });

  it("fetches once enabled", async () => {
    render({ params: base, enabled: false });
    render({ params: base, enabled: true });

    await flush();
    expect(latest.data).toBe(mapData);
  });

  it("reports the server's error", async () => {
    vi.mocked(fetchMap).mockRejectedValueOnce(new Error("boom"));

    render({ params: base });

    await flush();
    expect(latest.isError).toBe(true);
    expect((latest.error as Error).message).toBe("boom");
    expect(latest.data).toBeUndefined();
  });
});
