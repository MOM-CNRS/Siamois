// Mirrors fr.siamois.ui.bean.panel.models.panel.single.PhasePanel's own real GET route
// ("/phase/" + id — PhasePanel.entityRessourceUri()). Phase has no standalone list panel of its
// own (it's always viewed scoped to a project), so `list` is a placeholder that should not be
// reachable from the UI yet — same convention as FIND_ROUTES/RECORDING_UNIT_ROUTES.
export const PHASE_ROUTES = {
  list: "/phase",
  detail: (id: string | number) => `/phase/${id}`,
};
