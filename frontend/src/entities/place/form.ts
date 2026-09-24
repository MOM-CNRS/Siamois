// Place's own panel-name -> label map, the same job entities/project/form.ts's panelLabel and
// entities/phase/form.ts's phasePanelLabel do for their own entities — parseLayout itself is
// entity-agnostic and reused as-is from entities/project/form.ts.
//
// Mirrors SpatialUnit.DETAILS_FORM's single panel.
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
};

export function placePanelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
