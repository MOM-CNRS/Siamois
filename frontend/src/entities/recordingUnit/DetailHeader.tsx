import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { EntityDetailHeader, type HeaderChanges } from "../../components/EntityDetailHeader";
import { toAnswerInput } from "../../fields/types";
import type { AnswerInputBody, FieldResource } from "../../fields/types";
import { patchRecordingUnit } from "./api";
import { useDeclaredTypes } from "../useDeclaredTypes";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import type { RecordingUnitDetail } from "./types";
import { queryKeys } from "../../api/queryKeys";
import { t } from "../../i18n";

// recordingUnitPanelHeader.xhtml's content on the shared fiche header: the identifier as the main
// chip, then the type. The identifier is written through RecordingUnitPatchRequest's flat
// `identifier` (the full identifier, unique in the project), the type as an answer on the type
// field (RecordingUnitForm.RECORDING_UNIT_TYPE_FIELD, resolved by `valueBinding`, not hardcoded) —
// both in one request.
export interface RecordingUnitDetailHeaderProps {
  entity: RecordingUnitDetail;
  onSaved: () => void;
}

export function RecordingUnitDetailHeader({ entity, onSaved }: RecordingUnitDetailHeaderProps) {
  const typesQuery = useQuery({
    queryKey: queryKeys.recordingUnitTypes(entity.projectId),
    queryFn: () => getRecordingUnitTypes(entity.projectId as string),
    enabled: entity.projectId != null,
  });
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );
  const declaredTypes = useDeclaredTypes("recording-unit-types", entity.projectId);
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;

  function save(changes: HeaderChanges) {
    const body: { identifier?: string; answers?: Record<string, AnswerInputBody> } = {};
    if (changes.primary !== undefined) body.identifier = changes.primary;
    if (changes.typeId !== undefined && typeField) {
      body.answers = { [typeField.id]: toAnswerInput(typeField, changes.typeValue) };
    }
    return patchRecordingUnit(entity.id, body);
  }

  return (
    <EntityDetailHeader
      entityType="recordingUnit"
      chipPrefix="recording-unit"
      entity={entity}
      primary={{ value: entity.fullIdentifier, label: t("common.identifier"), requiredMessage: t("header.identifierRequired") }}
      type={{ value: entity.type, field: typeField, organizationId, declaredTypes }}
      save={save}
      onSaved={onSaved}
    />
  );
}
