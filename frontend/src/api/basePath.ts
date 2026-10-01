// The JSF app's servlet context path (e.g. "/siamois" in dev, "" if deployed at root),
// injected once from the mount options by focus.xhtml (see the JSF integration phase).
// Everything under EntityTypeConfig.api and the auth bridge must build URLs through this,
// never a hardcoded "/siamois" prefix, so the same bundle works in dev and any deployment.
let basePath = "";

export function configureBasePath(path: string): void {
  basePath = path.endsWith("/") ? path.slice(0, -1) : path;
}

export function apiUrl(path: string): string {
  const suffix = path.startsWith("/") ? path : `/${path}`;
  return `${basePath}${suffix}`;
}
