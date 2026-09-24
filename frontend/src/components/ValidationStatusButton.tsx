import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import { patchValidation, requiresValidator } from "../api/validation";
import {
  VALIDATION_PRESENTATION,
  VALIDATION_STATUSES,
  validationPresentation,
  type ValidationStatusValue,
} from "./table/ValidationStatusBadge";

export interface ValidationStatusButtonProps {
  collectionPath: string;
  entityId: string | number;
  status?: string | null;
  // Already combined with the write mode by the caller: what this user may do right now.
  canEdit: boolean;
  canValidate: boolean;
}

// The fiche's status: its icon (validationButton.xhtml's look), and on click an overlay with the four
// statuses. An option is offered only when the rule allows it — edit right for en cours/terminé/
// annulé, validator right to reach or leave validé — the server enforcing the same rule on PATCH.
export function ValidationStatusButton({ collectionPath, entityId, status, canEdit, canValidate }: ValidationStatusButtonProps) {
  const overlayRef = useRef<OverlayPanel>(null);
  const queryClient = useQueryClient();
  const current = (VALIDATION_STATUSES.includes(status as ValidationStatusValue) ? status : "INCOMPLETE") as ValidationStatusValue;
  const presentation = validationPresentation(current);

  const allowed = (target: ValidationStatusValue) =>
    target !== current && (requiresValidator(current, target) ? canValidate : canEdit);
  const anyAllowed = VALIDATION_STATUSES.some(allowed);

  const mutation = useMutation({
    mutationFn: (target: ValidationStatusValue) => patchValidation(collectionPath, entityId, target),
    onSuccess: () => {
      overlayRef.current?.hide();
      void queryClient.invalidateQueries({ queryKey: ["entity-detail"] });
      void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
    },
  });

  return (
    <>
      <Button
        icon={presentation.icon}
        className={`rounded-button status-button ${presentation.modifier}`}
        text
        rounded
        tooltip={presentation.title}
        tooltipOptions={{ position: "bottom" }}
        aria-label={`Statut : ${presentation.title}`}
        disabled={!anyAllowed}
        onClick={(e) => overlayRef.current?.toggle(e)}
      />
      <OverlayPanel ref={overlayRef} className="validation-status-overlay">
        <ul className="validation-status-options" style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {VALIDATION_STATUSES.map((target) => {
            const option = VALIDATION_PRESENTATION[target];
            const isCurrent = target === current;
            return (
              <li key={target}>
                <Button
                  className={`validation-status-option status-button ${option.modifier}`}
                  icon={option.icon}
                  label={option.title}
                  text
                  disabled={!allowed(target) || mutation.isPending}
                  aria-pressed={isCurrent}
                  tooltip={
                    !isCurrent && requiresValidator(current, target) && !canValidate
                      ? "Réservé aux validateurs"
                      : undefined
                  }
                  tooltipOptions={{ showOnDisabled: true, position: "left" }}
                  style={{ fontWeight: isCurrent ? 600 : undefined, width: "100%", justifyContent: "flex-start" }}
                  onClick={() => mutation.mutate(target)}
                />
              </li>
            );
          })}
        </ul>
        {mutation.error && (
          <div className="validation-status-error" role="alert">
            {(mutation.error as Error).message}
          </div>
        )}
      </OverlayPanel>
    </>
  );
}
