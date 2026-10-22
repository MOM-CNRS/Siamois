import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { deleteViewState, getViewState, putViewState } from "../../api/resources/views";
import { SAVE_DELAY_MS, useUserViewState, type UserViewState } from "./useUserViewState";

let container: HTMLDivElement;
let root: Root;
let latest: UserViewState;

function Harness({ viewKey, organizationId }: { viewKey?: string; organizationId?: number }) {
  latest = useUserViewState(viewKey, organizationId);
  return null;
}

function mount(props: { viewKey?: string; organizationId?: number }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <Harness {...props} />
      </QueryClientProvider>,
    );
  });
}

async function settle() {
  // React Query notifies through timers: faked here, so they are advanced.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(10);
  });
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.mocked(getViewState).mockClear();
  vi.mocked(putViewState).mockClear();
  vi.mocked(deleteViewState).mockClear();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
});

describe("useUserViewState", () => {
  it("is settled at once, and asks for nothing, without a key or an organization", () => {
    mount({ viewKey: "ctx:project:global" });
    expect(latest.settled).toBe(true);
    expect(latest.saved).toBeUndefined();
    expect(getViewState).not.toHaveBeenCalled();
  });

  it("is not settled until the server answered, then exposes what was saved", async () => {
    vi.mocked(getViewState).mockResolvedValueOnce({ viewKey: "k1", state: { v: 1, limit: 50 } });
    mount({ viewKey: "k1", organizationId: 3 });
    expect(latest.settled).toBe(false);
    await settle();
    expect(latest.settled).toBe(true);
    expect(latest.saved).toEqual({ v: 1, limit: 50 });
    expect(getViewState).toHaveBeenCalledWith(3, "k1");
  });

  it("settles on nothing saved when the read fails", async () => {
    vi.mocked(getViewState).mockRejectedValueOnce(new Error("boom"));
    mount({ viewKey: "k2", organizationId: 3 });
    await settle();
    expect(latest.settled).toBe(true);
    expect(latest.saved).toBeUndefined();
  });

  it("sends a burst of changes once, with the last one, after the delay", async () => {
    mount({ viewKey: "k3", organizationId: 3 });
    await settle();

    act(() => latest.save({ v: 1, limit: 25 }));
    act(() => latest.save({ v: 1, limit: 50 }));
    await settle();
    expect(latest.saved).toEqual({ v: 1, limit: 50 });
    expect(putViewState).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(SAVE_DELAY_MS + 1);
    });
    expect(putViewState).toHaveBeenCalledTimes(1);
    expect(putViewState).toHaveBeenCalledWith(3, "k3", { v: 1, limit: 50 });
  });

  it("sends what is waiting when the list unmounts", async () => {
    mount({ viewKey: "k4", organizationId: 3 });
    await settle();
    act(() => latest.save({ v: 1, limit: 100 }));

    act(() => root.render(<div />));

    expect(putViewState).toHaveBeenCalledWith(3, "k4", { v: 1, limit: 100 });
  });

  it("reset drops the waiting save and deletes the saved state", async () => {
    mount({ viewKey: "k5", organizationId: 3 });
    await settle();
    act(() => latest.save({ v: 1, limit: 100 }));
    act(() => latest.reset());
    await settle();
    act(() => {
      vi.advanceTimersByTime(SAVE_DELAY_MS + 1);
    });

    expect(putViewState).not.toHaveBeenCalled();
    expect(deleteViewState).toHaveBeenCalledWith(3, "k5");
    expect(latest.saved).toBeUndefined();
  });
});
