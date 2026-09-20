// Shape of the options object focus.xhtml's inline script passes to window.SiamoisMainPanel.mount
// (plan §7.2). Fixed here now so the JSF integration phase and the generic panels agree on it;
// most fields are unused until later phases wire up their consumer.
export type PanelKind = "home" | "list" | "detail";

export interface PanelActions {
  closeOverview?: () => void;
  fullscreen?: () => void;
  duplicate?: () => void;
  refresh?: () => void;
  create?: () => void;
  settings?: () => void;
}

export interface MountOptions {
  panelKind: PanelKind;
  entityType: string;
  entityId?: string | number;
  overviewEntityType?: string;
  overviewEntityId?: string | number;
  organizationId?: number;
  basePath: string;
  csrf: { headerName: string; token: string };
  bookmarked?: boolean;
  actions?: PanelActions;
  onNavigate?: (entityType: string, id: string | number) => void;
}
