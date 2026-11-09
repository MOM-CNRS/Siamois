import {createRoot, type Root} from "react-dom/client";
import {createElement} from "react";
import {PrimeReactProvider} from "primereact/api";
import {QueryClientProvider} from "@tanstack/react-query";
import {queryClient} from "../api/queryClient";
import {configureBasePath} from "../api/basePath";
import {configureCsrf} from "../auth/sessionAuth";
import {resolveLocale, setLocale} from "../i18n";
import {ViewsDrawer} from "./ViewsDrawer";

const roots = new Map<HTMLElement, Root>();

/** Mounts the « Vues » drawer on a `.react-views-drawer-mount` div (focus.xhtml); it carries what the API client needs. */
export function mountViewsDrawer(container: HTMLElement): void {
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
      createElement(QueryClientProvider, { client: queryClient }, createElement(ViewsDrawer, { organizationId })),
    ),
  );
}

/**
 * Mounts every drawer div not mounted yet. The drawer's panel is rendered by JSF only while the « Vues »
 * mode is open, and again after each ajax update, so this runs on every DOM change as well as at load.
 */
export function mountAllViewsDrawers(): void {
  for (const [container, root] of roots) {
    if (!container.isConnected) {
      root.unmount();
      roots.delete(container);
    }
  }
  document.querySelectorAll<HTMLElement>(".react-views-drawer-mount:not([data-mounted='true'])").forEach((container) => {
    container.dataset.mounted = "true";
    mountViewsDrawer(container);
  });
}

export function watchViewsDrawers(): void {
  mountAllViewsDrawers();
  new MutationObserver(() => mountAllViewsDrawers()).observe(document.body, { childList: true, subtree: true });
}
