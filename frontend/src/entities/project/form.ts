// Mirrors the JSON shape FormUiDtoLayoutJson.serialize produces for FormResource.layoutJson
// (plan §6/§8 phase 6) — panels/rows/columns referencing fieldId into the sibling `fields`
// catalog from GET /api/v1/organizations/{id}/project-types. Kept generic (nothing Project-typed
// here): any future entity whose form is schema-driven the same way can reuse this parser.
export interface FormLayoutCol {
  className?: string | null;
  isRequired: boolean;
  isReadOnly: boolean;
  fieldId?: number | string | null;
}

export interface FormLayoutRow {
  columns: FormLayoutCol[];
}

export interface FormLayoutPanel {
  className?: string | null;
  name: string;
  canUserAddFields?: boolean | null;
  isSystemPanel?: boolean | null;
  rows: FormLayoutRow[];
}

export function parseLayout(layoutJson: string): FormLayoutPanel[] {
  if (!layoutJson) return [];
  return JSON.parse(layoutJson) as FormLayoutPanel[];
}

// Panel `name` is an i18n message code (JSF resolves it against the message bundle), not a
// ready-to-display label — ActionUnitDetailsForm only ever uses these four. Falls back to the
// raw code for any panel this map doesn't know about, rather than crashing on the next one added.
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
  "common.label.localisation": "Localisation",
  "actionunit.header.administrative": "Administratif",
  "actionunit.header.documentation": "Documentation",
};

export function panelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
