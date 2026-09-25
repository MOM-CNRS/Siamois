// Shape of the options object focus.xhtml's inline script passes to window.SiamoisMainPanel.mount
// (plan §7.2/§8 phase 8).
export type PanelKind = "home" | "list" | "detail";

// The buttons of one panel titlebar. A key is present only when that button should show — the
// callbacks are built client-side from the displayed entity itself (its `_permissions`, the write
// mode), never from what JSF mounted, so the toolbar stays valid after a client-side navigation.
export interface PanelActions {
  // Same kind, same project as the displayed entity (JSF's creationUnitKind button).
  create?: () => void;
  duplicate?: () => void;
  // Project only: opens its settings page (a JSF redirect, through PanelBridge.openProjectSettings).
  settings?: () => void;
  // Main panel titlebar only, and only in focus mode (an overview entity promoted to main):
  // puts it back in the overview and restores the previous main — legacy focus.xhtml's
  // closeFocusLink (bi-arrows-angle-contract).
  closeFocus?: () => void;
  // Overview pane titlebar only (panelContent.xhtml): close it, or promote it to the main pane
  // (App's client-side focus swap).
  closeOverview?: () => void;
  fullscreen?: () => void;
}

// Everything PanelToolbar needs to render one titlebar's bookmark button + resolve a display
// title, for either the main panel or the overview — mirrors panelModel.ressourceUri()/
// resolveTitleOrTitleCode()/isBookmarked(). `bookmarked` undefined means "not known yet" (a list
// or Home reached client-side has no REST resource carrying the flag): PanelToolbar then asks
// GET /api/v1/bookmarks/status itself.
export interface PanelChrome {
  resourceUri: string;
  title: string;
  bookmarked?: boolean;
}

// Everything one panel's own header needs to render its toolbar (plan §7.3, revised: "the
// toolbar is part of the panel header" — App.tsx no longer renders PanelToolbar as a strip above
// the panel; HomePanel/EntityListPanel/EntityDetailPanel each take this and put it in their own
// PrimeReact <Panel>'s `icons`, next to their own header content — matching the real markup's
// single sideview-titlebar div that holds both the toolbar form and the displayHeader() include).
// App fills in the pane-level actions (closeFocus/closeOverview/fullscreen); a detail panel merges
// its own entity actions (create/duplicate/settings) on top.
export interface PanelToolbarSlot {
  chrome: PanelChrome;
  organizationId?: number;
  actions?: PanelActions;
}

// The only calls React still makes into the JSF session (reactPanelActions.xhtml's
// p:remoteCommands). None is bound to the entity JSF mounted with the page — each takes its target
// as a param — so they stay valid across client-side navigation.
export interface PanelBridge {
  // Keeps FlowBean's parentOrOverview in sync with a client-side-opened overview (plan §8 phase
  // 5), for F5 and the history — fire-and-forget.
  setOverview?: (entityType: string, id: string | number) => void;
  closeOverview?: () => void;
  // NavBean#redirectToActionUnitSettingsFromRequest: a server-side mode switch + redirect.
  openProjectSettings?: (projectId: string | number) => void;
}

export interface MountOptions {
  // Identifies which server-side panel bean this mount belongs to (AbstractPanel.panelIndex).
  panelIndex?: string;
  panelKind: PanelKind;
  entityType: string;
  entityId?: string | number;
  overviewEntityType?: string;
  overviewEntityId?: string | number;
  organizationId?: number;
  overviewOrganizationId?: number;
  // FlowBean.isWriteMode — the topbar's global read/write switch. Everything editable in the app is
  // gated on it in JSF (headerEditControls.xhtml, entityDataTable.xhtml's "writeMode" rendering
  // rule, tableToolbar.xhtml), and React honours the same gate through WriteModeProvider.
  // Defaults to false when the attribute is missing: showing a read-only UI to someone who may edit
  // is a nuisance, offering edit controls to someone in read mode is a lie the API then rejects.
  writeMode?: boolean;
  basePath: string;
  csrf: { headerName: string; token: string };
  // Chrome of the view JSF mounted with (its bookmark state included) — used until React
  // navigates away from it; a detail panel replaces it with its entity's own anyway.
  main: PanelChrome;
  overview?: PanelChrome;
  bridge?: PanelBridge;
  // AbstractPanel.goBackUrl — set by FocusViewBean from the `back=` URL param, i.e. this page was
  // loaded (or F5'd) in focus mode. The main toolbar's closeFocus then does a real navigation to it,
  // since the client-side focus stack App keeps doesn't survive a reload.
  goBackUrl?: string;
}
