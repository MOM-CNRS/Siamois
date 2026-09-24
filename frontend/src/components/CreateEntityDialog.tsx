import { Dialog } from "primereact/dialog";
import { getEntityType } from "../entities/registry";
import type { CreatePrefill, ListScope } from "../entities/types";

export interface CreateEntityDialogProps {
  entityType: string;
  visible: boolean;
  organizationId?: number;
  // The parent the new entity belongs to (its project), exactly what the entity's createForm
  // already reads from a scoped list — see CreateFormContext.scope.
  scope?: ListScope;
  // What the new entity is linked to (a row action's parent/child/UE/place) — see CreatePrefill.
  prefill?: CreatePrefill;
  onCreated: (id: string | number) => void;
  onHide: () => void;
}

// The titlebar's "Créer" (JSF's creationUnitKind button → GenericNewUnitDialogBean's dialog):
// hosts the entity's own `list.createForm` — the same form the list toolbar opens in an overlay —
// in a dialog, since a titlebar button has no room to anchor an overlay next to it.
export function CreateEntityDialog({ entityType, visible, organizationId, scope, prefill, onCreated, onHide }: CreateEntityDialogProps) {
  const config = getEntityType(entityType);
  if (!config?.list.createForm) return null;
  return (
    <Dialog
      header={`Nouveau : ${config.labels.singular}`}
      visible={visible}
      onHide={onHide}
      style={{ width: "32rem", maxWidth: "calc(100vw - 32px)" }}
      className="create-entity-dialog"
    >
      {/* Unmounted while hidden, so each opening starts from an empty form. */}
      {visible && config.list.createForm({ organizationId, scope, prefill, onCreated, onCancel: onHide })}
    </Dialog>
  );
}
