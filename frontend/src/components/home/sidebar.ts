/**
 * Opens the history drawer of the JSF sidebar (the sidebar belongs to JSF: its entries are ajax links). Does
 * nothing when the drawer is already showing — the link would close it — or when there is no JSF sidebar.
 */
export function openHistoryDrawer(): void {
  const sidebar = document.getElementById("subSidebarForm");
  if (sidebar && !sidebar.classList.contains("is-collapsed") && sidebar.classList.contains("mode-historique")) return;
  document.querySelector<HTMLElement>('[id$="historyLinkNav"]')?.click();
}
