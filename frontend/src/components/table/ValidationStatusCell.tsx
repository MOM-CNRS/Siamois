import { useWriteMode } from "../../panels/writeMode";
import { ValidationStatusButton } from "../ValidationStatusButton";
import { ValidationStatusBadge } from "./ValidationStatusBadge";

export interface ValidationStatusCellProps {
  entityType: string;
  collectionPath: string;
  row: {
    id: string | number;
    validated?: string | null;
    _permissions?: { canEdit?: boolean; canValidate?: boolean };
  };
}

// A list row's status: the fiche's own status picker when this user may change it right now (write
// mode AND the row's own rights), the read-only badge otherwise — never a disabled button.
export function ValidationStatusCell({ entityType, collectionPath, row }: ValidationStatusCellProps) {
  const writeMode = useWriteMode();
  const canEdit = writeMode && row._permissions?.canEdit === true;
  const canValidate = writeMode && row._permissions?.canValidate === true;
  if (!canEdit && !canValidate) {
    return <ValidationStatusBadge status={row.validated} />;
  }
  return (
    // The row itself reacts to clicks (selection, overview); the picker's own click stays here.
    <span onClick={(e) => e.stopPropagation()} style={{ display: "inline-flex" }}>
      <ValidationStatusButton
        entityType={entityType}
        collectionPath={collectionPath}
        entityId={row.id}
        status={row.validated}
        canEdit={canEdit}
        canValidate={canValidate}
        compact
      />
    </span>
  );
}
