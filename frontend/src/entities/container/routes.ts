// Mirrors fr.siamois.ui.bean.panel.models.panel.single.ContainerPanel's own real GET route
// ("/container/" + id — ContainerPanel.entityRessourceUri()). Container has no standalone list
// panel of its own (it's always viewed scoped to a project), so `list` is a placeholder that
// should not be reachable from the UI yet — same convention as PHASE_ROUTES/FIND_ROUTES.
export const CONTAINER_ROUTES = {
  list: "/container",
  detail: (id: string | number) => `/container/${id}`,
};
