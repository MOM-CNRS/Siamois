import type {ReactNode} from "react";
import {getEntityType} from "../../entities/registry";
import {t} from "../../i18n";
import {createdUnitOption, type UnitOption} from "./api";
import {FitToViewport} from "./FitToViewport";

// Whether a unit can be created on the spot, in this project.
export const canCreateUnit = (projectId?: string | number | null): boolean =>
  projectId != null && getEntityType("recordingUnit")?.list.createForm != null;

// The last line of a unit picker's list: « Nouvelle unité ».
export function NewUnitItem({ onClick }: { onClick: () => void }): ReactNode {
  return (
    <button type="button" className="sia-inline-picker-item" onMouseDown={(e) => e.preventDefault()} onClick={onClick}>
      <i className="bi bi-plus-square" aria-hidden="true" /> {t("recordingUnit.graph.dialog.createUnit")}
    </button>
  );
}

interface NewUnitFormProps {
  projectId?: string | number | null;
  organizationId?: number;
  // The unit was created: it is only picked, the user still confirms the link.
  onCreated: (unit: UnitOption) => void;
  onCancel: () => void;
}

// The recording unit's own create form, for a unit to link that doesn't exist yet.
export function NewUnitForm({ projectId, organizationId, onCreated, onCancel }: NewUnitFormProps) {
  const createForm = getEntityType("recordingUnit")?.list.createForm;
  if (!createForm) return null;
  return (
    <FitToViewport>
      {createForm({
        organizationId,
        scope: projectId != null ? { entityType: "project", id: projectId } : undefined,
        onCreated: (id) => void createdUnitOption(id).then(onCreated),
        onCancel,
      })}
    </FitToViewport>
  );
}
