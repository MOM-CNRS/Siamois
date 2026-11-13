import {beforeAll, describe, expect, it} from "vitest";
import type {FieldCatalog} from "../../../entities/shared/types";
import {registerDefaultFieldRenderers} from "../../../fields/registerDefaultRenderers";
import type {FieldResource} from "../../../fields/types";
import {
  bulkAnswerInput,
  bulkEditableFields,
  bulkEditContext,
  canBulkEdit,
  commonProjectId,
  deletableRows,
  editableRows,
  isRelationField,
  spansSeveralProjects,
} from "./bulkSelection";

/**
 * A catalog field of the given kind.
 */
function field(id: string, answerType: string, extra: Partial<FieldResource> = {}): FieldResource {
  return { id, resourceType: "field", label: id, answerType, isSystemField: false, ...extra };
}

const comment = field("comment", "TEXT");
const parents = field("parents", "SELECT_MULTIPLE_RECORDING_UNIT");
const authors = field("authors", "SELECT_MULTIPLE_PERSON");
const keywords = field("keywords", "SELECT_MULTIPLE");

beforeAll(() => registerDefaultFieldRenderers());

describe("bulkEditableFields", () => {
  it("offers the editable fields in the catalog's order", () => {
    const catalog: FieldCatalog = {
      fields: {
        comment,
        parents,
        identifier: field("identifier", "TEXT", { readOnly: true }),
        project: field("project", "SELECT_ONE_ACTION_UNIT", { valueBinding: "actionUnit" }),
        anterior: field("anterior", "SELECT_MULTIPLE_STRATIGRAPHY", { valueBinding: "anterior" }),
        address: field("address", "SELECT_ADDRESS"),
      },
      columns: [
        { fieldId: "parents", columnId: "parents", visible: true, order: 2 },
        { fieldId: "comment", columnId: "comment", visible: false, order: 1 },
      ],
    };

    expect(bulkEditableFields(catalog).map((f) => f.id)).toEqual(["comment", "parents"]);
  });

  it("offers nothing without a catalog", () => {
    expect(bulkEditableFields(undefined)).toEqual([]);
  });
});

describe("isRelationField", () => {
  it("tells the links to other entities apart from plain values and concepts", () => {
    expect(isRelationField(parents)).toBe(true);
    expect(isRelationField(authors)).toBe(true);
    expect(isRelationField(keywords)).toBe(false);
    expect(isRelationField(comment)).toBe(false);
  });
});

describe("bulkAnswerInput", () => {
  it("sets a scalar value on every entity", () => {
    expect(bulkAnswerInput(comment, "set", "Remblai")).toEqual({ value: "Remblai" });
    expect(bulkAnswerInput(comment, "set", null)).toEqual({ value: null });
  });

  it("adds or removes the picked references without touching the others", () => {
    const picked = [{ id: "7" }, { resourceId: "9" }];

    expect(bulkAnswerInput(parents, "add", picked)).toEqual({ add: ["7", "9"] });
    expect(bulkAnswerInput(parents, "remove", picked)).toEqual({ remove: ["7", "9"] });
  });

  it("replaces the whole list when asked to", () => {
    expect(bulkAnswerInput(parents, "set", [{ id: "7" }])).toEqual({ values: ["7"] });
    expect(bulkAnswerInput(parents, "set", [])).toEqual({ values: [] });
  });
});

describe("the selection's rights and project", () => {
  const rowA = { id: 1, projectId: 10, type: { id: "3" }, _permissions: { canEdit: true, canDelete: true } };
  const rowB = { id: 2, projectId: 10, type: { id: "3" }, _permissions: { canEdit: true, canDelete: false } };
  const rowC = { id: 3, project: { resourceId: 20 }, _permissions: { canEdit: false } };

  it("keeps only the rows the caller may edit, or delete", () => {
    expect(editableRows([rowA, rowB, rowC]).map((r) => r.id)).toEqual([1, 2]);
    expect(deletableRows([rowA, rowB, rowC]).map((r) => r.id)).toEqual([1]);
  });

  it("finds the one project the rows share, and none across several", () => {
    expect(commonProjectId([rowA, rowB], "recordingUnit")).toBe("10");
    expect(commonProjectId([rowA, rowC], "recordingUnit")).toBeUndefined();
    expect(commonProjectId([{ id: 5 }, { id: 6 }], "project")).toBeUndefined();
  });

  it("tells a selection spanning several projects from one in a single project, or in none", () => {
    expect(spansSeveralProjects([rowA, rowB], "recordingUnit")).toBe(false);
    expect(spansSeveralProjects([rowA, rowC], "recordingUnit")).toBe(true);
    expect(spansSeveralProjects([{ id: 7 }, { id: 8 }], "place")).toBe(false);
  });

  it("edits a project-scoped relation only inside one project", () => {
    expect(canBulkEdit(parents, [rowA, rowB], "recordingUnit")).toBe(true);
    expect(canBulkEdit(parents, [rowA, rowC], "recordingUnit")).toBe(false);
    expect(canBulkEdit(authors, [rowA, rowC], "recordingUnit")).toBe(true);
  });

  it("edits with the selection's common project and type, and no single entity", () => {
    expect(bulkEditContext([rowA, rowB], "recordingUnit", 4)).toEqual({
      organizationId: 4,
      entityType: "recordingUnit",
      projectId: "10",
      typeConceptId: "3",
    });
  });
});
