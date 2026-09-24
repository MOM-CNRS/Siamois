// Mirrors fr.siamois.ui.bean.panel.models.panel.single.SpatialUnitPanel's own real GET route
// ("/spatial-unit/" + id — SpatialUnitPanel.entityRessourceUri()). Place has no standalone list
// panel of its own in this React migration (the project fiche's "Lieux" tab is a static table,
// not this entity's own list — see config.tsx), so `list` is a placeholder that should not be
// reachable from the UI yet, same convention as PHASE_ROUTES/CONTAINER_ROUTES.
export const PLACE_ROUTES = {
  list: "/spatial-unit",
  detail: (id: string | number) => `/spatial-unit/${id}`,
};
