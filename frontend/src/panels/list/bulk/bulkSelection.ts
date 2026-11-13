import type {FieldCatalog} from "../../../entities/shared/types";
import {editContextOf, type FieldEditContext, typeIdOf} from "../../../fields/editContext";
import {hasFieldRenderer} from "../../../fields/registry";
import {stratigraphyPositionOf} from "../../../fields/stratigraphy";
import {type AnswerInputBody, type FieldResource, isOwningProjectField, toAnswerInput} from "../../../fields/types";
import type {RowRecord} from "../useEntityListData";

// The reference kinds whose values must belong to the edited entity's own project (the server
// refuses any other): a bulk edit of one of them needs the whole selection in one project.
const PROJECT_SCOPED_TYPES = new Set([
  "SELECT_ONE_RECORDING_UNIT",
  "SELECT_ONE_DOCUMENT",
  "SELECT_MULTIPLE_RECORDING_UNIT",
  "SELECT_MULTIPLE_SPECIMEN",
  "SELECT_MULTIPLE_PHASE",
  "SELECT_MULTIPLE_CONTAINER",
]);

/**
 * How a bulk edit writes a field: `set` gives every entity the same value (for a multi-valued
 * field, the same whole list); `add` and `remove` change a multi-valued field by the values picked,
 * leaving every other value of each entity alone — the bulk counterpart of linking or unlinking.
 */
export type BulkEditMode = "set" | "add" | "remove";

interface RowPermissions {
  _permissions?: { canEdit?: boolean; canDelete?: boolean };
}

interface RowType {
  type?: { id?: string | number; resourceId?: string | number } | null;
}

/**
 * Whether the field holds several values (a SELECT_MULTIPLE_* kind): the ones a bulk edit can add
 * to or remove from.
 */
export function isMultiValued(field: FieldResource): boolean {
  return field.answerType.startsWith("SELECT_MULTIPLE");
}

/**
 * Whether the field links the entity to other entities (a reference to recording units, finds,
 * phases, containers, places, persons…) rather than holding a plain value or a concept: the bulk
 * bar lists these apart, as its « Relations ».
 */
export function isRelationField(field: FieldResource): boolean {
  return isMultiValued(field) && field.answerType !== "SELECT_MULTIPLE";
}

/**
 * Whether the field's values must come from the edited entity's own project.
 */
export function isProjectScoped(field: FieldResource): boolean {
  return PROJECT_SCOPED_TYPES.has(field.answerType);
}

/**
 * The fields of the list's catalog a bulk edit can write, in the catalog's order: those the list
 * could edit in a cell (an editor, not read-only), minus the stratigraphy (a relationship with its
 * own qualifiers, not an answer) and the owning project (set once, at creation).
 */
export function bulkEditableFields(catalog: FieldCatalog | undefined): FieldResource[] {
  if (!catalog) return [];
  const ordered = [...catalog.columns].sort((a, b) => a.order - b.order).map((column) => column.fieldId);
  const ids = [...new Set([...ordered, ...Object.keys(catalog.fields)])];
  return ids
    .map((id) => catalog.fields[id])
    .filter((field): field is FieldResource => field != null)
    .filter(
      (field) =>
        !field.readOnly &&
        hasFieldRenderer(field.answerType) &&
        stratigraphyPositionOf(field) == null &&
        !isOwningProjectField(field),
    );
}

/**
 * The stratigraphy fields of the list's catalog (anterior, posterior, synchronous units), in the
 * catalog's order: no answers to patch, but relationships a bulk action adds or removes.
 */
export function bulkStratigraphyFields(catalog: FieldCatalog | undefined): FieldResource[] {
  if (!catalog) return [];
  return Object.values(catalog.fields).filter((field) => stratigraphyPositionOf(field) != null);
}

/**
 * The selected rows the caller may edit (`_permissions.canEdit`): a bulk edit skips the others.
 */
export function editableRows(rows: readonly RowRecord[]): RowRecord[] {
  return rows.filter((row) => row.id != null && (row as RowPermissions)._permissions?.canEdit === true);
}

/**
 * The selected rows the caller may delete (`_permissions.canDelete`): a bulk deletion skips the others.
 */
export function deletableRows(rows: readonly RowRecord[]): RowRecord[] {
  return rows.filter((row) => row.id != null && (row as RowPermissions)._permissions?.canDelete === true);
}

/**
 * The ids of the rows, in order.
 */
export function idsOf(rows: readonly RowRecord[]): (string | number)[] {
  return rows.map((row) => row.id).filter((id): id is string | number => id != null);
}

/**
 * Whether the rows belong to more than one project (rows with no project at all, places, count as one).
 */
export function spansSeveralProjects(rows: readonly RowRecord[], entityType: string): boolean {
  return new Set(rows.map((row) => editContextOf(row, entityType, undefined).projectId)).size > 1;
}

/**
 * The one project every row belongs to, or undefined when they belong to several (or to none, a
 * place). For a list of projects, each row is its own project.
 */
export function commonProjectId(rows: readonly RowRecord[], entityType: string): string | undefined {
  const projects = new Set(rows.map((row) => editContextOf(row, entityType, undefined).projectId));
  if (projects.size !== 1) return undefined;
  return [...projects][0];
}

/**
 * The one type every row has, or undefined when they differ: what narrows a vocabulary field to
 * its type-specific configuration.
 */
function commonTypeId(rows: readonly RowRecord[]): string | undefined {
  const types = new Set(rows.map((row) => typeIdOf((row as RowType).type)));
  if (types.size !== 1) return undefined;
  return [...types][0];
}

/**
 * Where a bulk edit's field editor works: the selection's common project and type, and no single
 * edited entity (a « Nouveau » from a picker is not linked to any one of them).
 */
export function bulkEditContext(
  rows: readonly RowRecord[],
  entityType: string,
  organizationId: number | undefined,
): FieldEditContext {
  return {
    organizationId,
    entityType,
    projectId: commonProjectId(rows, entityType),
    typeConceptId: commonTypeId(rows),
  };
}

/**
 * Whether the field can be edited in bulk on these rows: a project-scoped reference only when they
 * all belong to the same project.
 */
export function canBulkEdit(field: FieldResource, rows: readonly RowRecord[], entityType: string): boolean {
  return !isProjectScoped(field) || !spansSeveralProjects(rows, entityType);
}

/**
 * The answer a bulk edit sends to every entity: the value as is (`set`), or the picked values added
 * to (`add`) or removed from (`remove`) each entity's own.
 */
export function bulkAnswerInput(field: FieldResource, mode: BulkEditMode, value: unknown): AnswerInputBody {
  if (mode === "set" || !isMultiValued(field)) return toAnswerInput(field, value);
  const ids = toAnswerInput(field, value).values ?? [];
  return mode === "add" ? { add: ids } : { remove: ids };
}
