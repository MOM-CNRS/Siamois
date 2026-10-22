// The JSF page paths of an entity type (its controller's mapping, e.g. /recording-unit/{id}):
// the list at `/<segment>`, one entity at `/<segment>/<id>`.
export function jsfRoutes(segment: string) {
  return {
    list: `/${segment}`,
    detail: (id: string | number) => `/${segment}/${id}`,
  };
}
