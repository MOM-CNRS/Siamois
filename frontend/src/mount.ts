import { createRoot, type Root } from "react-dom/client";
import { createElement } from "react";
// Default PrimeReact theme for now (2026-09-15: tried inheriting the site's own --siamois-* CSS
// variables since primefaces-siamois-theme defines the same :root custom-property names PrimeReact
// themes consume — technically correct, but the result looked broken/inconsistent in practice, e.g.
// missing hover/focus states the lara theme's own CSS expects that the site theme doesn't define the
// same way. Reverted to the standard bundled theme; revisit real visual parity later if it matters.
import "primereact/resources/themes/lara-light-blue/theme.css";
import "primereact/resources/primereact.min.css";
import "primeicons/primeicons.css";
import "./theme.css";
import { App } from "./App";
import { fetchSessionToken } from "./auth/sessionAuth";
import { configureTokenRefresh } from "./api/client";
import { configureBasePath } from "./api/basePath";
import type { PanelActions } from "./panel/panelActions";

export interface MountOptions {
  recordingUnitId: number;
  /**
   * The Spring Security CSRF token value for the current JSF session (host page must read it from
   * its own request/session — e.g. a `${_csrf.token}` EL expression — and pass it here; it's needed
   * once to bootstrap the JWT exchange, and again silently whenever the JWT is refreshed).
   */
  csrfToken: string;
  /**
   * The app's servlet context path (e.g. "/siamois" in dev, "" if deployed at the domain root) — the
   * host page must pass `#{request.contextPath}` here. Every request this panel makes is prefixed with
   * it; omitting this silently 404s everything as soon as the app isn't deployed at the domain root.
   */
  basePath?: string;
  /** JSF-bridged toolbar actions (close/duplicate/refresh/bookmark) — see panelActions.ts. Omit only
   *  in the standalone dev harness, where the toolbar simply won't render those buttons. */
  actions?: PanelActions;
  /** Initial bookmark state (FlowBean.parentOrOverview.isBookmarked() at mount time). */
  bookmarked?: boolean;
}

const roots = new WeakMap<HTMLElement, Root>();

/**
 * Entry point called by the JSF overview panel host to render the React Recording Unit panel into
 * `container`. Exposed on `window` (see bottom of file) so it can be invoked from a plain <script>
 * tag in the JSF template without a module bundler on that side.
 */
export async function mountRecordingUnitPanel(container: HTMLElement, options: MountOptions): Promise<void> {
  configureBasePath(options.basePath ?? "");
  configureTokenRefresh(options.csrfToken);
  await fetchSessionToken(options.csrfToken);

  const root = createRoot(container);
  roots.set(container, root);
  root.render(
    createElement(App, {
      recordingUnitId: options.recordingUnitId,
      actions: options.actions,
      bookmarked: options.bookmarked ?? false,
    }),
  );
}

export function unmountRecordingUnitPanel(container: HTMLElement): void {
  const root = roots.get(container);
  if (root) {
    root.unmount();
    roots.delete(container);
  }
}

declare global {
  interface Window {
    SiamoisRecordingUnitPanel: {
      mount: typeof mountRecordingUnitPanel;
      unmount: typeof unmountRecordingUnitPanel;
    };
  }
}

window.SiamoisRecordingUnitPanel = {
  mount: mountRecordingUnitPanel,
  unmount: unmountRecordingUnitPanel,
};
