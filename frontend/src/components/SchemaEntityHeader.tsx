import { EntityDetailHeader, useFormFields, type HeaderChanges } from "./EntityDetailHeader";
import { useDeclaredTypes } from "../entities/useDeclaredTypes";
import { toAnswerInput, type AnswerInputBody } from "../fields/types";
import { t } from "../i18n";

export interface SchemaEntityHeaderProps {
  entityType: string;
  // The JSF class prefix of the entity's chips (`<prefix>-chip-alt`, `<prefix>-type-chip`).
  chipPrefix: string;
  entity: {
    id: string | number;
    projectId?: string | number | null;
    type?: { id?: unknown; resolvedLabel?: string | null } | null;
    organization?: { id: string } | null;
    _permissions?: { canEdit?: boolean };
  };
  // The identifier: the main chip, or the secondary one when the entity has a `title`.
  identifier: string;
  // Phase and document have a title (a form field, saved as an answer): it becomes the main chip
  // and the identifier moves to a secondary chip, shown once there is a title.
  title?: { value: string | null | undefined };
  // The project's types catalog this entity's form comes from ("find-types"…).
  typesSegment: string;
  patch: (id: string | number, body: { identifier?: string; answers?: Record<string, AnswerInputBody> }) => Promise<unknown>;
  onSaved: () => void;
}

/**
 * The header of an entity whose form is served per type (find, container, phase, document): the
 * identifier is a flat `identifier` of the patch, the type an answer on the form's type field —
 * both written in one request.
 */
export function SchemaEntityHeader({
  entityType,
  chipPrefix,
  entity,
  identifier,
  title,
  typesSegment,
  patch,
  onSaved,
}: SchemaEntityHeaderProps) {
  const { typeField, titleField } = useFormFields(typesSegment, entity);
  const declaredTypes = useDeclaredTypes(typesSegment, entity.projectId);
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;

  function save(changes: HeaderChanges) {
    const body: { identifier?: string; answers?: Record<string, AnswerInputBody> } = {};
    const answers: Record<string, AnswerInputBody> = {};
    if (title) {
      if (changes.primary !== undefined && titleField) answers[titleField.id] = { value: changes.primary || null };
      if (changes.secondary !== undefined) body.identifier = changes.secondary;
    } else if (changes.primary !== undefined) {
      body.identifier = changes.primary;
    }
    if (changes.typeId !== undefined && typeField) answers[typeField.id] = toAnswerInput(typeField, changes.typeValue);
    if (Object.keys(answers).length > 0) body.answers = answers;
    return patch(entity.id, body);
  }

  return (
    <EntityDetailHeader
      entityType={entityType}
      chipPrefix={chipPrefix}
      entity={entity}
      primary={
        title
          ? {
              value: title.value ?? "",
              fallback: identifier,
              optional: true,
              label: t("common.title"),
              requiredMessage: t("header.identifierRequired"),
            }
          : { value: identifier, label: t("common.identifier"), requiredMessage: t("header.identifierRequired") }
      }
      secondary={
        title
          ? {
              value: identifier,
              label: t("common.identifier"),
              requiredMessage: t("header.identifierRequired"),
              hideInView: !title.value,
            }
          : undefined
      }
      type={{ value: entity.type, field: typeField, organizationId, declaredTypes }}
      save={save}
      onSaved={onSaved}
    />
  );
}
