// The left-hand half of JSF's merged "statut | identifiant" column (entityDataTable.xhtml's
// statusIdActionsCol → /panel/header/validationButton.xhtml). Read-only in lists, exactly like the
// JSF list, which includes that button with disabled="true": changing the status is a fiche action
// (components/ValidationStatusButton.tsx).
//
// Icon and modifier class names are copied from validationButton.xhtml verbatim so the existing
// .status-button.validated/.complete/.incomplete theme rules apply unchanged; "cancelled" is new.

export type ValidationStatusValue = "INCOMPLETE" | "COMPLETE" | "VALIDATED" | "CANCELLED";

// The workflow order, used by the fiche's status picker.
export const VALIDATION_STATUSES: ValidationStatusValue[] = ["INCOMPLETE", "COMPLETE", "VALIDATED", "CANCELLED"];

export const VALIDATION_PRESENTATION: Record<ValidationStatusValue, { icon: string; modifier: string; title: string }> = {
  INCOMPLETE: { icon: "bi bi-circle", modifier: "incomplete", title: "En cours" },
  COMPLETE: { icon: "bi bi-circle-fill", modifier: "complete", title: "Terminé" },
  VALIDATED: { icon: "bi bi-check-circle", modifier: "validated", title: "Validé" },
  CANCELLED: { icon: "bi bi-x-circle", modifier: "cancelled", title: "Annulé" },
};

// Anything else (null, an enum value added server-side later) degrades to INCOMPLETE, which is
// also TraceableEntity's own default — never to a blank cell.
export function validationPresentation(status?: string | null) {
  return VALIDATION_PRESENTATION[(status as ValidationStatusValue) ?? "INCOMPLETE"] ?? VALIDATION_PRESENTATION.INCOMPLETE;
}

export interface ValidationStatusBadgeProps {
  status?: string | null;
}

export function ValidationStatusBadge({ status }: ValidationStatusBadgeProps) {
  const presentation = validationPresentation(status);
  return (
    <i
      className={`validation-status-badge status-button ${presentation.modifier} ${presentation.icon}`}
      title={presentation.title}
      aria-label={presentation.title}
    />
  );
}
