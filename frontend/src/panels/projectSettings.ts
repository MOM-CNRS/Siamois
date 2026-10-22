import { apiUrl } from "../api/basePath";

// The project's settings are a server-rendered (JSF) page: SettingsController#goToProjectSettings checks the
// right, switches the application to settings mode and forwards to it — a plain navigation, nothing to call first.
export function openProjectSettings(projectId: string | number): void {
  window.location.assign(apiUrl(`/settings/project/${projectId}`));
}

// The configurable table (ConfigurableTable) each project fiche's type is declared on. Project and place
// are not here: they have no type configured through the project's tables.
const TABLE_OF_ENTITY: Record<string, string> = {
  recordingUnit: "UE",
  find: "MOBILIER",
  phase: "PHASE",
  container: "CONTENANT",
  document: "DOCUMENT",
  study: "STUDY",
};

export function settingsTableOf(entityType: string): string | undefined {
  return TABLE_OF_ENTITY[entityType];
}

/**
 * The project's table configuration, opened on a type (its concept id) and, when given, on one of its fields
 * (a custom field id) — SettingsController#goToProjectTablesSettings checks the right.
 */
export function openTablesSettings(projectId: string | number, table: string, typeId?: string | number, fieldId?: string | number): void {
  const params = new URLSearchParams({ table });
  if (typeId != null) params.set("typeId", String(typeId));
  if (typeId != null && fieldId != null) params.set("fieldId", String(fieldId));
  window.location.assign(apiUrl(`/settings/project/${projectId}/tables?${params.toString()}`));
}
