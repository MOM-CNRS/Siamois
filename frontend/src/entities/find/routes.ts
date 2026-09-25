// Mirrors fr.siamois.ui.bean.panel.models.panel.single.SpecimenPanel's own real GET route
// ("/specimen/" + id — SpecimenPanel.entityRessourceUri()). Find has no standalone list panel of
// its own (it's always viewed scoped to a project), so `list` is a placeholder that should not be
// reachable from the UI yet — same convention as RECORDING_UNIT_ROUTES.
export const FIND_ROUTES = {
  list: "/specimen",
  detail: (id: string | number) => `/specimen/${id}`,
};
