import { apiUrl } from "../api/basePath";

// The project's settings are a server-rendered (JSF) page: SettingsController#goToProjectSettings checks the
// right, switches the application to settings mode and forwards to it — a plain navigation, nothing to call first.
export function openProjectSettings(projectId: string | number): void {
  window.location.assign(apiUrl(`/settings/project/${projectId}`));
}
