// Find's own panel-name -> label map, the same job entities/project/form.ts's panelLabel and
// entities/recordingUnit/form.ts's recordingUnitPanelLabel do for their own entities —
// parseLayout itself is entity-agnostic and reused as-is from entities/project/form.ts.
//
// Mirrors SpecimenDetailsForm.build()'s panels (common.header.general appears twice — a real,
// system-panel-scoped SpecimenDetailsForm.java quirk mirroring RecordingUnitDetailsForm's own
// duplicate "Général" panel, matched as-is rather than invented a disambiguating label, per this
// migration's parity rule).
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
  "common.header.chronology": "Chronologie",
  "common.header.measurement": "Mesures",
};

export function findPanelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
