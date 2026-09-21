import { createRoot, type Root } from "react-dom/client";
import { createElement } from "react";
import "primereact/resources/themes/lara-light-blue/theme.css";
import "primereact/resources/primereact.min.css";
import "primeicons/primeicons.css";
import "./styles/main-panel.css";
import { configureBasePath } from "./api/basePath";
import { configureCsrf } from "./auth/sessionAuth";
import { registerDefaultFieldRenderers } from "./fields/registerDefaultRenderers";
import { registerEntityType } from "./entities/registry";
import { projectEntityConfig } from "./entities/project/config";
import type { MountOptions } from "./mountOptions";
import { App } from "./App";

registerDefaultFieldRenderers();
// One registerEntityType call per entity module (plan §3/§8 phase 4) — the next entity to
// migrate is a config-only addition here, nothing else in this file changes.
registerEntityType(projectEntityConfig);

// One generic mount function parameterized by panel kind + entity type (plan §3) — never one
// mount function per entity. focus.xhtml calls this once isReactPanelEnabled() gates a panel
// in (plan §7); until then, nothing else in the JSF app references this bundle.
const roots = new Map<HTMLElement, Root>();

function mount(container: HTMLElement, options: MountOptions): void {
  configureBasePath(options.basePath);
  configureCsrf(options.csrf);

  const root = createRoot(container);
  roots.set(container, root);
  root.render(createElement(App, { options }));
}

function unmount(container: HTMLElement): void {
  const root = roots.get(container);
  if (!root) return;
  root.unmount();
  roots.delete(container);
}

declare global {
  interface Window {
    SiamoisMainPanel: { mount: typeof mount; unmount: typeof unmount; _queue?: [HTMLElement, MountOptions][] };
  }
}

// focus.xhtml's own inline bootstrap script (plan §7.2/§8 phase 8) runs synchronously, before
// this module — an ES module script always loads/executes asynchronously relative to a classic
// script — has had a chance to run. It installs a stub SiamoisMainPanel that just queues
// mount() calls; drain that queue now that the real implementation exists, so no mount() call
// made before this module was ready gets silently dropped.
const queued = window.SiamoisMainPanel?._queue ?? [];
window.SiamoisMainPanel = { mount, unmount };
for (const [container, options] of queued) {
  mount(container, options);
}
