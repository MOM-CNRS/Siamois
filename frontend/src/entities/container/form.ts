// Container's own panel-name -> label map, the same job entities/phase/form.ts's phasePanelLabel
// does for Phase — parseLayout itself is entity-agnostic and reused as-is from
// entities/project/form.ts.
//
// Mirrors ContainerDetailsForm.build()'s two panels. "common.header.general" -> "Général" matches
// the label every other entity's own PANEL_LABELS map already uses for that exact message code.
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
  "common.header.dimensions": "Dimensions",
};

export function containerPanelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
