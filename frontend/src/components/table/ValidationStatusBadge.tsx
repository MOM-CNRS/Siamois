// The left-hand half of JSF's merged "statut | identifiant" column (entityDataTable.xhtml's
// statusIdActionsCol → /panel/header/validationButton.xhtml). Read-only here, exactly like the
// JSF list, which includes that button with disabled="true": toggling validation is a detail-panel
// action (panelModel.toggleValidate()), not a list one, and the REST API exposes no endpoint for
// it yet.
//
// Icon and modifier class names are copied from validationButton.xhtml verbatim so the existing
// .status-button.validated/.complete/.incomplete theme rules apply unchanged.

export type ValidationStatusValue = "INCOMPLETE" | "COMPLETE" | "VALIDATED";

const PRESENTATION: Record<ValidationStatusValue, { icon: string; modifier: string; title: string }> = {
  VALIDATED: { icon: "bi bi-check-circle", modifier: "validated", title: "Validé" },
  COMPLETE: { icon: "bi bi-circle-fill", modifier: "complete", title: "Complet" },
  INCOMPLETE: { icon: "bi bi-circle", modifier: "incomplete", title: "Incomplet" },
};

export interface ValidationStatusBadgeProps {
  // Anything else (null, an enum value added server-side later) degrades to INCOMPLETE, which is
  // also TraceableEntity's own default — never to a blank cell.
  status?: string | null;
}

export function ValidationStatusBadge({ status }: ValidationStatusBadgeProps) {
  const presentation = PRESENTATION[(status as ValidationStatusValue) ?? "INCOMPLETE"] ?? PRESENTATION.INCOMPLETE;
  return (
    <i
      className={`validation-status-badge status-button ${presentation.modifier} ${presentation.icon}`}
      title={presentation.title}
      aria-label={presentation.title}
    />
  );
}
