// Mirrors fr.siamois.ui.bean.panel.models.panel.single.RecordingUnitPanel's own real GET route
// ("/recording-unit/" + id, singular — RecordingUnitPanel.ressourceUri()/getRoute()). RecordingUnit
// has no standalone list panel of its own today (it's always viewed scoped to a project), so
// `list` is a placeholder that should not be reachable from the UI yet.
export const RECORDING_UNIT_ROUTES = {
  list: "/recording-unit",
  detail: (id: string | number) => `/recording-unit/${id}`,
};
