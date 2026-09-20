import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { configureBasePath } from "../../api/basePath";
import { projectHomeWidgets } from "./homeWidgets";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function renderWidget(index: 0 | 1, onNavigate = vi.fn()) {
  const widgets = projectHomeWidgets({ organizationId: 7, onNavigate });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(<QueryClientProvider client={queryClient}>{widgets[index].render()}</QueryClientProvider>);
  });
  return onNavigate;
}

beforeEach(() => {
  mockedApiFetch.mockReset();
  configureBasePath("/siamois");
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
  configureBasePath("");
});

describe("projectHomeWidgets", () => {
  it("requests projects sorted by creationTime desc, scoped to the organization", async () => {
    mockedApiFetch.mockResolvedValue({ data: [], meta: { total: 0, limit: 5, offset: 0 } });
    renderWidget(0);
    await flush();

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("sort=creationTime%3Adesc");
    expect(path).toContain("organizationId=7");
    expect(path).toContain("limit=5");
  });

  it("recent-projects widget shows an empty state when there are none", async () => {
    mockedApiFetch.mockResolvedValue({ data: [], meta: { total: 0, limit: 5, offset: 0 } });
    renderWidget(0);
    await flush();

    expect(container.textContent).toContain("Aucun projet");
  });

  it("recent-projects widget navigates to the project on click", async () => {
    mockedApiFetch.mockResolvedValue({
      data: [{ resourceType: "projects", id: "9", name: "Fouille A", fullIdentifier: "FA-1", identifier: "FA-1" }],
      meta: { total: 1, limit: 5, offset: 0 },
    });
    const onNavigate = renderWidget(0);
    await flush();

    const button = container.querySelector("button") as HTMLElement;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("project", "9");
  });

  it("count-card widget shows the total and a link to the list, prefixed with the base path", async () => {
    mockedApiFetch.mockResolvedValue({ data: [], meta: { total: 12, limit: 5, offset: 0 } });
    renderWidget(1);
    await flush();

    expect(container.textContent).toContain("12");
    const link = container.querySelector("a") as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe("/siamois/action-unit");
    expect(link.textContent).toBe("Voir la liste");
  });
});
