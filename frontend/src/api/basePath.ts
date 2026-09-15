/**
 * The app's servlet context path (e.g. "/siamois" in dev — `server.servlet.context-path` in
 * application.yaml, configurable per environment). Every request this panel makes must be prefixed
 * with it; a hardcoded leading-slash path like `/api/v1/...` silently 404s whenever the app isn't
 * deployed at the domain root. Set once at mount time (see mount.ts) from `#{request.contextPath}`,
 * which the JSF host page already knows.
 */
let basePath = "";

export function configureBasePath(path: string): void {
  basePath = path ?? "";
}

export function getBasePath(): string {
  return basePath;
}
