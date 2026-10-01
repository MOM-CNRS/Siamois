import { createRoot, type Root } from "react-dom/client";
import { createElement } from "react";
import { PrimeReactProvider } from "primereact/api";
import "./styles/bundle";
import { getLocale, setLocale } from "./i18n";
import { applyPrimeLocale } from "./i18n/primeLocale";
import { configureBasePath } from "./api/basePath";
import { configureCsrf } from "./auth/sessionAuth";
import { registerDefaultFieldRenderers } from "./fields/registerDefaultRenderers";
import { registerEntityType } from "./entities/registry";
import { projectEntityConfig } from "./entities/project/config";
import { recordingUnitEntityConfig } from "./entities/recordingUnit/config";
import { findEntityConfig } from "./entities/find/config";
import { phaseEntityConfig } from "./entities/phase/config";
import { containerEntityConfig } from "./entities/container/config";
import { placeEntityConfig } from "./entities/place/config";
import { MountContractError, parseMountOptions, type ActionResolver, type MountOptions } from "./mountOptions";
import { App } from "./App";

registerDefaultFieldRenderers();
// One registerEntityType call per entity module (plan §3/§8 phase 4) — the next entity to
// migrate is a config-only addition here, nothing else in this file changes.
registerEntityType(projectEntityConfig);
registerEntityType(recordingUnitEntityConfig);
registerEntityType(findEntityConfig);
registerEntityType(phaseEntityConfig);
registerEntityType(containerEntityConfig);
registerEntityType(placeEntityConfig);

// One generic mount function parameterized by panel kind + entity type (plan §3) — never one
// mount function per entity. focus.xhtml's bootstrap (reactPanelBootstrap.js) calls it once per
// mount div.
const roots = new Map<HTMLElement, Root>();

// JSF replaces a mount div whenever it re-renders the flow (the write-mode switch, an institution
// change): the old container leaves the DOM without anyone calling unmount, and its React root
// (its tree, its listeners) would live on. Every mount sweeps those away.
function unmountDetached(): void {
  for (const container of roots.keys()) {
    if (!container.isConnected) unmount(container);
  }
}

function mount(container: HTMLElement, options: MountOptions): void {
  unmountDetached();
  if (options.locale) setLocale(options.locale);
  applyPrimeLocale(getLocale());
  configureBasePath(options.basePath);
  configureCsrf(options.csrf);

  // A container mounted twice (a bootstrap run again on the same div) keeps a single root.
  unmount(container);
  const root = createRoot(container);
  roots.set(container, root);
  // PrimeReactProvider with its defaults: some components read the context unguarded (an open
  // OverlayPanel throws on the first scroll without it).
  root.render(createElement(PrimeReactProvider, null, createElement(App, { options })));
}

// What reactPanelBootstrap.js calls: the mount div carries its own options (reactPanelMount.xhtml).
function mountFromDataset(container: HTMLElement): void {
  let options: MountOptions;
  try {
    options = parseMountOptions(container.dataset, (name) => {
      const fn = name ? (window as unknown as Record<string, unknown>)[name] : undefined;
      return typeof fn === "function" ? (fn as ReturnType<ActionResolver>) : undefined;
    });
  } catch (error) {
    if (!(error instanceof MountContractError)) throw error;
    console.error(error.message);
    container.textContent = error.message;
    return;
  }
  mount(container, options);
}

function unmount(container: HTMLElement): void {
  const root = roots.get(container);
  if (!root) return;
  root.unmount();
  roots.delete(container);
}

declare global {
  interface Window {
    SiamoisMainPanel: {
      mount: typeof mount;
      mountFromDataset: typeof mountFromDataset;
      unmount: typeof unmount;
      _queue?: HTMLElement[];
    };
  }
}

// reactPanelBootstrap.js may run before this module (an ES module always executes after classic
// scripts): it then installs a stub SiamoisMainPanel that queues the containers to mount. Drain that
// queue now that the real implementation exists, so nothing is silently dropped.
const queued = window.SiamoisMainPanel?._queue ?? [];
window.SiamoisMainPanel = { mount, mountFromDataset, unmount };
for (const container of queued) {
  mountFromDataset(container);
}
