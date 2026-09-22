// Shape of the options object focus.xhtml's inline script passes to window.SiamoisMainPanel.mount
// (plan §7.2/§8 phase 8).
export type PanelKind = "home" | "list" | "detail";

// Bridged via p:remoteCommand from the main panel's own titlebar (focus.xhtml) — a key is present
// only when JSF's own `rendered` condition for that button allows it (e.g. `create` is omitted
// entirely when creationUnitKind is null or the session isn't in write mode), so PanelToolbar
// never has to re-derive permission logic client-side.
export interface PanelActions {
  duplicate?: () => void;
  refresh?: () => void;
  create?: () => void;
  settings?: () => void;
  // Only meaningful when panelKind is "list" — bridged from that entity's OWN table toolbar
  // create button (ActionUnitListPanel.configureTableColumns's toolbarCreateConfig), a different
  // gate than `create` above (which is the main panel titlebar's, tied to creationUnitKind and
  // never rendered at all for a list panel in JSF).
  listCreate?: () => void;
  // Keeps FlowBean's parentOrOverview in sync with a client-side-opened overview (plan §8 phase
  // 5) — fire-and-forget, not awaited by the caller. entityType is accepted for a future
  // multi-entity bridge; today the remoteCommand behind this is action-unit-specific.
  setOverview?: (entityType: string, id: string | number) => void;
}

// The overview pane's own titlebar (panelContent.xhtml) has two extra buttons the main panel's
// titlebar doesn't: closing the overview, and popping it into full focus mode.
export interface OverviewActions extends PanelActions {
  closeOverview?: () => void;
  fullscreen?: () => void;
}

// Everything PanelToolbar needs to render one titlebar's bookmark button + resolve a display
// title, for either the main panel or the overview — mirrors panelModel.ressourceUri()/
// resolveTitleOrTitleCode()/isBookmarked().
export interface PanelChrome {
  resourceUri: string;
  title: string;
  bookmarked: boolean;
}

// Everything one panel's own header needs to render its toolbar (plan §7.3, revised: "the
// toolbar is part of the panel header" — App.tsx no longer renders PanelToolbar as a strip above
// the panel; HomePanel/EntityListPanel/EntityDetailPanel each take this and put it in their own
// PrimeReact <Panel>'s `icons`, next to their own header content — matching the real markup's
// single sideview-titlebar div that holds both the toolbar form and the displayHeader() include).
export interface PanelToolbarSlot {
  chrome: PanelChrome;
  organizationId?: number;
  actions?: PanelActions | OverviewActions;
}

export interface MountOptions {
  // Identifies which server-side panel bean this mount belongs to (AbstractPanel.panelIndex) —
  // only needed to filter the "siamois-set-overview-done" DOM event a page with several mounted
  // panels (flow.xhtml's tab stack) would otherwise dispatch for every mount at once.
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
  main: PanelChrome;
  actions?: PanelActions;
  overview?: PanelChrome;
  overviewActions?: OverviewActions;
}
