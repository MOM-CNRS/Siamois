import { useEffect, useRef } from "react";
import { OverlayPanel } from "primereact/overlaypanel";
import { getEntityType } from "../entities/registry";
import type { CreatePrefill, ListScope } from "../entities/types";

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
  const overlayRef = useRef<OverlayPanel>(null);

  useEffect(() => {
    if (anchor) overlayRef.current?.show(null as never, anchor);
    else overlayRef.current?.hide();
  }, [anchor]);

  if (!config?.list.createForm) return null;
  return (
    // Keys typed in the form must not bubble through the React tree to whatever hosts it (a cell
    // editor cancels its edit on Escape): the overlay is portalled out of its DOM box, not out of
    // its React tree.
    <div onKeyDown={(e) => e.stopPropagation()} style={{ display: "contents" }}>
      <OverlayPanel
        ref={overlayRef}
        className="entity-list-panel-create-overlay create-entity-overlay"
        onHide={onHide}
        aria-label={`Nouveau : ${config.labels.singular}`}
      >
        {/* Unmounted while closed, so each opening starts from an empty form. */}
        {anchor &&
          config.list.createForm({ organizationId, scope, prefill, onCreated, onCancel: () => overlayRef.current?.hide() })}
      </OverlayPanel>
    </div>
  );
}
