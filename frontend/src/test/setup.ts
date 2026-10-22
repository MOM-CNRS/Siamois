import { afterEach, vi } from "vitest";
import { queryClient } from "../api/queryClient";
import { notifySpy } from "./notifySpy";

// Vitest setup shared by every test file.

// React only checks act() usage when told it runs under a test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// jsdom's CSS parser can't read the stylesheets PrimeReact injects at runtime (cascade layers) and
// reports each one through console.error. They carry no information for the tests and would bury
// real warnings, so they're dropped here — and only them.
const consoleError = console.error.bind(console);
console.error = (...args: unknown[]) => {
  const first = args[0];
  const text = first instanceof Error ? first.message : String(first);
  if (text.includes("Could not parse CSS stylesheet")) return;
  consoleError(...args);
};

// The app's shared cache (catalogs are fetched through it) must not carry one test's data into the next.
afterEach(() => {
  queryClient.clear();
  Object.values(notifySpy).forEach((spy) => spy.mockClear());
});

// Toasts are recorded in `notifySpy` rather than drawn: a test asserts on what the user was told.
vi.mock("../notify/NotifyProvider", async () => {
  const { useEffect } = await import("react");
  const { notifySpy: spy } = await import("./notifySpy");
  return {
    NotifyProvider: ({ children }: { children: unknown }) => children,
    useNotify: () => spy,
    useToastOnChange: (message: string | null | undefined, severity: keyof typeof spy = "error") => {
      useEffect(() => {
        if (message) spy[severity](message);
      }, [message, severity]);
    },
  };
});

// No test reaches the server for what a user kept on a view (GET/PUT /api/v1/view-states) or for the saved
// views (/api/v1/views): by default nothing was kept and there is no view. A test of that state overrides these mocks.
vi.mock("../api/resources/views", () => ({
  getViewState: vi.fn((_organizationId: number, viewKey: string) => Promise.resolve({ viewKey, state: null })),
  putViewState: vi.fn(() => Promise.resolve()),
  deleteViewState: vi.fn(() => Promise.resolve()),
  listViews: vi.fn(() => Promise.resolve({ data: [], totalCount: 0, limit: 50, offset: 0 })),
  getView: vi.fn(() => Promise.reject(new Error("no such view"))),
  createView: vi.fn(),
  updateView: vi.fn(),
  deleteView: vi.fn(() => Promise.resolve()),
}));

// Nor the tabs a project configured for a fiche (/api/v1/fiche-configs): by default none, and nobody may edit.
vi.mock("../api/resources/ficheConfigs", () => ({
  getFicheConfig: vi.fn(() => Promise.resolve({ tabs: null, canEdit: false })),
  putFicheConfig: vi.fn(() => Promise.resolve({ tabs: null, canEdit: true })),
  deleteFicheConfig: vi.fn(() => Promise.resolve()),
}));
