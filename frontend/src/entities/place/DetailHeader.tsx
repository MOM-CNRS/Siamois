import { useMemo } from "react";
import { EntityDetailHeader, isTypeField } from "../../components/EntityDetailHeader";
import type { FieldResource } from "../../fields/types";
import { patchPlaceHeader } from "./api";
import type { PlaceDetail } from "./types";
import { t } from "../../i18n";

// The place's header on the shared fiche header: its name (which is its identifier) as the main
// chip, then its type. The type field comes with the detail (SpatialUnit.DETAILS_FORM is static).
export function PlaceDetailHeader({ entity, onSaved }: { entity: PlaceDetail; onSaved: () => void }) {
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(entity.fields ?? {}).find(isTypeField),
    [entity.fields],
  );
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;

  return (
    <EntityDetailHeader
      entityType="place"
      chipPrefix="spatial-unit"
      entity={entity}
      primary={{ value: entity.name ?? "", label: t("common.name"), requiredMessage: t("header.nameRequired") }}
      type={{ value: entity.type, field: typeField, organizationId }}
      save={(changes) => patchPlaceHeader(entity.id, { name: changes.primary, typeId: changes.typeId })}
      onSaved={onSaved}
    />
  );
}
