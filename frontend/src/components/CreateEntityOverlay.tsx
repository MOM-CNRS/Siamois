import { getEntityType } from "../entities/registry";
import { AnchoredFormOverlay } from "./AnchoredFormOverlay";
import type { CreatePrefill, ListScope } from "../entities/types";
import { t } from "../i18n";

export interface CreateEntityOverlayProps {
  entityType: string;
  // The element the overlay opens next to (the button or field the creation was started from);
  // null = closed. Every creation opens next to what started it, never in a modal.
  anchor: HTMLElement | null;
  organizationId?: number;
  // The parent the new entity belongs to (its project), exactly what the entity's createForm
  // already reads from a scoped list — see CreateFormContext.scope.
  scope?: ListScope;
  // What the new entity is linked to (a row action's parent/child/UE/place) — see CreatePrefill.
  prefill?: CreatePrefill;
  onCreated: (id: string | number) => void;
  onHide: () => void;
}

/**
 * Hosts an entity's own `list.createForm` in an overlay anchored on what started the creation —
 * the same surface the list toolbar's "Créer" uses — for the fiche titlebar's "Créer", a list
 * row's "new child/parent/find" actions and a reference field's « Nouveau ».
 */
export function CreateEntityOverlay({ entityType, anchor, organizationId, scope, prefill, onCreated, onHide }: CreateEntityOverlayProps) {
  const config = getEntityType(entityType);
  if (!config?.list.createForm) return null;
  return (
    <AnchoredFormOverlay anchor={anchor} ariaLabel={t("create.newOf", { label: config.labels.singular })} onHide={onHide}>
      {(close) => config.list.createForm!({ organizationId, scope, prefill, onCreated, onCancel: close })}
    </AnchoredFormOverlay>
  );
}
