/**
 * Bridge back into the JSF host's FlowBean-backed panel chrome (close/duplicate/refresh/bookmark),
 * wired from panelContent.xhtml's p:remoteCommands (see mount.ts callers). React never mutates that
 * server-side state directly — every one of these is a fire-and-forget call into a JSF action, mirroring
 * exactly what the old JSF-rendered toolbar icons did.
 */
export interface PanelActions {
  /** Collapses the overview splitter server-side (FlowBean.parentOrOverview = null). */
  close: () => void;
  /** Runs RecordingUnitPanel.duplicate() and replaces this whole overview slot with the copy. */
  duplicate: () => void;
  /** Re-fetches the unit server-side and re-renders this slot (also remounts React with fresh data). */
  refresh: () => void;
  /** Toggles this panel's sidebar bookmark. */
  toggleBookmark: () => void;
}
