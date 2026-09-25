// Mirrors fr.siamois.ui.redirection.ActionUnitController's real GET routes — shared by
// config.tsx (routes.list/detail) and homeWidgets.tsx (the "voir la liste" link and recent-
// projects navigation), which would otherwise have to import one another.
export const PROJECT_ROUTES = {
  list: "/action-unit",
  detail: (id: string | number) => `/action-unit/${id}`,
};
