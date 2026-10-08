import { useEffect, useMemo, useState, type KeyboardEvent, type ReactNode } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { messageForError } from "../api/errors";
import { queryKeys } from "../api/queryKeys";
import { getEffectiveForm } from "../entities/typeCatalog";
import { getEntityType } from "../entities/registry";
import { SelectOneConceptRenderer } from "../fields/renderers";
import type { FieldResource } from "../fields/types";
import type { FilterOption } from "../fields/optionSources";
import { useCanEdit } from "../panels/writeMode";
import { t } from "../i18n";

/** A text shown as a chip, edited in place as a text input. */
export interface HeaderText {
  value: string;
  // Aria label of the input while editing.
  label: string;
  // Shown when the draft is blank.
  requiredMessage: string;
  // A blank value is allowed (the main chip then shows `fallback`) — e.g. an optional title.
  optional?: boolean;
  // What the chip shows while `value` is blank.
  fallback?: string;
  // Not shown as a chip while viewing (still an input while editing) — the identifier next to a
  // title is only worth a chip once there is a title to tell it from.
  hideInView?: boolean;
}

/** What changed in the header, handed to the entity's `save`. Only the changed parts are set. */
export interface HeaderChanges {
  primary?: string;
  secondary?: string;
  // The new type: the concept's id, or null to clear it.
  typeId?: string | null;
  // The type picker's own value (the concept reference), for savers that write it as an answer.
  typeValue?: unknown;
}

export interface EntityDetailHeaderProps {
  entityType: string;
  // The JSF class prefix of the entity's chips (`<prefix>-chip-alt`, `<prefix>-type-chip`).
  chipPrefix: string;
  entity: { _permissions?: { canEdit?: boolean } };
  // The main chip: the entity's identifier (the project's name).
  primary: HeaderText;
  // A second text chip: the project's identifier.
  secondary?: HeaderText;
  type?: {
    // The type as the entity carries it (`{ id, resolvedLabel }`).
    value: { id?: unknown; resourceId?: unknown; resolvedLabel?: string | null } | null | undefined;
    // The type's field, as the fiche's form serves it; none → the type is shown but not editable.
    field?: FieldResource;
    organizationId?: number;
    // The types the project declared for the table: the picker offers exactly these.
    declaredTypes?: readonly FilterOption[];
  };
  // Read-only chips after the type (the project's location).
  extra?: ReactNode;
  // Writes every changed part in ONE request.
  save: (changes: HeaderChanges) => Promise<unknown>;
  onSaved: () => void;
}

/**
 * The header every fiche shares: the main chip, an optional second one, the type chip, then ONE
 * pencil at the far right (hidden until the header is hovered, so a stray click can't start an
 * edit). In edit mode the pencil is the one validate button: it writes every changed part in a
 * single request (Enter does the same from an input, Escape drops the draft). The identifier and
 * the type are always editable, as far as the user may edit the entity.
 */
export function EntityDetailHeader({
  entityType,
  chipPrefix,
  entity,
  primary,
  secondary,
  type,
  extra,
  save,
  onSaved,
}: EntityDetailHeaderProps) {
  const canEdit = useCanEdit(entity);
  const [editing, setEditing] = useState(false);
  const [primaryDraft, setPrimaryDraft] = useState(primary.value);
  const [secondaryDraft, setSecondaryDraft] = useState(secondary?.value ?? "");
  const [typeDraft, setTypeDraft] = useState<unknown>(type?.value);
  const [error, setError] = useState<string | null>(null);
  const isEditing = editing && canEdit;

  const typeValue = type?.value;
  function resetDrafts() {
    setPrimaryDraft(primary.value);
    setSecondaryDraft(secondary?.value ?? "");
    setTypeDraft(typeValue);
    setError(null);
  }

  // A reloaded entity (after a save, or another fiche) drops whatever draft was in flight.
  useEffect(resetDrafts, [primary.value, secondary?.value, typeValue]); // eslint-disable-line react-hooks/exhaustive-deps

  const mutation = useMutation({
    mutationFn: save,
    onSuccess: () => {
      setEditing(false);
      setError(null);
      onSaved();
    },
    onError: (err: unknown) => setError(messageForError(err, t("header.saveFailed"))),
  });

  function validate() {
    const first = primaryDraft.trim();
    const second = secondaryDraft.trim();
    if (!first && !primary.optional) return setError(primary.requiredMessage);
    if (secondary && !second && !secondary.optional) return setError(secondary.requiredMessage);
    const changes: HeaderChanges = {};
    if (first !== primary.value) changes.primary = first;
    if (secondary && second !== secondary.value) changes.secondary = second;
    if (type?.field && conceptId(typeDraft) !== conceptId(typeValue)) {
      changes.typeId = conceptId(typeDraft);
      changes.typeValue = typeDraft;
    }
    if (Object.keys(changes).length === 0) {
      setEditing(false);
      return;
    }
    mutation.mutate(changes);
  }

  function cancel() {
    setEditing(false);
    resetDrafts();
  }

  function onKeyDown(e: KeyboardEvent<HTMLElement>) {
    if (!isEditing) return;
    if (e.key === "Escape") cancel();
    if (e.key === "Enter" && (e.target as HTMLElement).tagName === "INPUT") validate();
  }

  const typeLabel = type?.value?.resolvedLabel;
  const typeEditable = isEditing && type?.field != null;

  return (
    <div className={`entity-detail-header ${chipPrefix}-detail-header sia-hstack`} onKeyDown={onKeyDown}>
      {isEditing ? (
        <InputText
          className="entity-detail-header-primary-input"
          value={primaryDraft}
          aria-label={primary.label}
          onChange={(e) => setPrimaryDraft(e.target.value)}
        />
      ) : (
        <Chip label={primary.value || primary.fallback || ""} className={`${chipPrefix}-chip-alt entity-nav-chip`} icon={getEntityType(entityType)?.icon} />
      )}
      {secondary &&
        (isEditing || !secondary.hideInView) &&
        (isEditing ? (
          <InputText
            className="entity-detail-header-secondary-input"
            value={secondaryDraft}
            aria-label={secondary.label}
            onChange={(e) => setSecondaryDraft(e.target.value)}
          />
        ) : (
          <Chip label={secondary.value} className={`mr-2 ${chipPrefix}-type-chip`} />
        ))}
      {typeEditable ? (
        <span className="entity-detail-header-category sia-inline-center">
          <SelectOneConceptRenderer
            field={type!.field!}
            value={typeDraft as never}
            readOnly={mutation.isPending}
            required={false}
            organizationId={type!.organizationId}
            declaredOptions={type!.declaredTypes}
            onChange={setTypeDraft}
          />
        </span>
      ) : (
        (typeLabel || (canEdit && type?.field != null)) && (
          <span className="entity-detail-header-category sia-inline-center">
            <Chip label={typeLabel ?? t("header.noType")} className={`mr-2 ${chipPrefix}-type-chip`} />
          </span>
        )
      )}
      {!isEditing && extra}
      {error && <Message severity="error" text={error} />}
      {canEdit && (
        <Button
          icon={isEditing ? "pi pi-check" : "pi pi-pencil"}
          className="p-button-text entity-detail-header-edit"
          aria-label={isEditing ? t("header.validate") : t("header.editHeader")}
          loading={mutation.isPending}
          onClick={() => (isEditing ? validate() : setEditing(true))}
        />
      )}
    </div>
  );
}

/**
 * The type and title fields of an entity whose form is configured per type (`typesSegment`, e.g.
 * "find-types"): located in the fiche's effective form by its valueBinding rather than by a
 * hardcoded id, so a renumbering of the system-field catalog doesn't silently break the header.
 * Same query key as the fiche tab, so React Query serves both from one fetch.
 */
export function useFormFields(
  typesSegment: string,
  entity: { projectId?: string | number | null; type?: { id?: unknown } | null },
): { typeField?: FieldResource; titleField?: FieldResource } {
  const typeId = entity.type?.id != null ? String(entity.type.id) : undefined;
  const formQuery = useQuery({
    queryKey: queryKeys.effectiveForm(typesSegment, entity.projectId, typeId),
    queryFn: () => getEffectiveForm(typesSegment, entity.projectId!, typeId ?? null),
    enabled: entity.projectId != null,
  });
  return useMemo(() => {
    const fields = Object.values(formQuery.data?.fields ?? {});
    return { typeField: fields.find(isTypeField), titleField: fields.find((f) => f.valueBinding === "title") };
  }, [formQuery.data]);
}

/**
 * An entity's type is bound to `type` (recording unit, phase, container, project) or to `category`
 * (find, place, document) — the same concept, named after the table it belongs to.
 */
export function isTypeField(field: FieldResource): boolean {
  return field.valueBinding === "type" || field.valueBinding === "category";
}

/** The concept id of a picker value or of an entity's `type` reference. */
export function conceptId(value: unknown): string | null {
  if (value != null && typeof value === "object") {
    const ref = value as { resourceId?: unknown; id?: unknown };
    if (ref.resourceId != null) return String(ref.resourceId);
    if (ref.id != null) return String(ref.id);
  }
  return null;
}
