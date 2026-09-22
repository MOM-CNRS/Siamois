// RU's own panel-name → label map, the same job entities/project/form.ts's panelLabel does for
// Project — `parseLayout` itself is entity-agnostic and reused as-is from there.
//
// Mirrors RecordingUnitDetailsForm.build()'s four panels exactly. Note the general panel AND the
// dates panel both use the code "common.header.general" server-side (RecordingUnitDetailsForm's
// own datesPanel() literally passes COMMON_HEADER_GENERAL, not a dates-specific code) — so both
// render the same "Général" header. That's server data, not a client bug: matched as-is rather
// than invented a disambiguating label, per this migration's parity rule (reproduce the JSF
// screen, don't redesign it).
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
  "recordingunit.panel.chronology": "Chronologie",
  "recordingunit.panel.measurements": "Mesures",
};

export function recordingUnitPanelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
