// Phase's own panel-name -> label map, the same job entities/project/form.ts's panelLabel and
// entities/recordingUnit/form.ts's recordingUnitPanelLabel do for their own entities —
// parseLayout itself is entity-agnostic and reused as-is from entities/project/form.ts.
//
// Mirrors PhaseDetailsForm.build()'s two panels. "common.header.general" -> "Général" matches
// the label every other entity's own PANEL_LABELS map already uses for that exact message code
// (kept consistent across entities rather than binding messages_fr.properties' own slightly
// different wording for it, "Informations générales" — a pre-existing, deliberate simplification
// this migration reproduces as-is, not something to fix per-entity).
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
  "common.header.chronologie": "Chronologie",
};

export function phasePanelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
