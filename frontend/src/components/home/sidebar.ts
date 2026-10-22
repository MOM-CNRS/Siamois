export type SidebarDrawer = "historique" | "views";

const LINK_IDS: Record<SidebarDrawer, string> = { historique: "historyLinkNav", views: "viewsLinkNav" };

/**
 * Opens a drawer of the JSF sidebar (the sidebar belongs to JSF: its entries are ajax links). Does nothing when
 * that drawer is already showing — the link would close it — or when there is no JSF sidebar.
 */
export function openSidebarDrawer(drawer: SidebarDrawer): void {
  const sidebar = document.getElementById("subSidebarForm");
  if (sidebar && !sidebar.classList.contains("is-collapsed") && sidebar.classList.contains(`mode-${drawer}`)) return;
  document.querySelector<HTMLElement>(`[id$="${LINK_IDS[drawer]}"]`)?.click();
}
