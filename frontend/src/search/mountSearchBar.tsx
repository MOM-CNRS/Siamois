import { createRoot, type Root } from "react-dom/client";
import { createElement } from "react";
import { PrimeReactProvider } from "primereact/api";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "../api/queryClient";
import { configureBasePath } from "../api/basePath";
import { configureCsrf } from "../auth/sessionAuth";
import { setLocale, resolveLocale } from "../i18n";
import { GlobalSearchBar } from "./GlobalSearchBar";
import { HistoryNav } from "./HistoryNav";

const roots = new Map<HTMLElement, Root>();

/**
 * Mounts the header search bar on a `.react-search-bar-mount` div (topbar.xhtml). Called by the
 * bundle itself when it loads: the topbar precedes the script in the page, so the div is there.
 * The div carries what the API client needs, like the panel's own mount div (reactPanelMount.xhtml).
 */
export function mountSearchBar(container: HTMLElement): void {
  roots.get(container)?.unmount();
  const data = container.dataset;
  if (data.locale) setLocale(resolveLocale(data.locale));
  configureBasePath(data.basePath ?? "");
  configureCsrf({ headerName: data.csrfHeader ?? "", token: data.csrfToken ?? "" });

  const organizationId = data.organizationId ? Number(data.organizationId) : undefined;
  const root = createRoot(container);
  roots.set(container, root);
  root.render(
    createElement(
      PrimeReactProvider,
      null,
      createElement(
        QueryClientProvider,
        { client: queryClient },
        // Back / Forward arrows, then the search: the topbar's own order after the organisation select.
        createElement(
          "div",
          { className: "sia-topbar-search" },
          createElement(HistoryNav, { organizationId }),
          createElement("div", { className: "sia-topbar-search-field" }, createElement(GlobalSearchBar, { organizationId })),
        ),
      ),
    ),
  );
}

export function mountAllSearchBars(): void {
  for (const [container] of roots) {
    if (!container.isConnected) {
      roots.get(container)?.unmount();
      roots.delete(container);
    }
  }
  document.querySelectorAll<HTMLElement>(".react-search-bar-mount:not([data-mounted='true'])").forEach((container) => {
    container.dataset.mounted = "true";
    mountSearchBar(container);
  });
}
