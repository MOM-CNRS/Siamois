import { beforeEach, describe, expect, it, vi } from "vitest";
import type { RowActionContext, RowActionDef } from "../types";
import { unlinkDocument } from "./api";
import { documentsTab } from "./documentsTab";

// relationTab hands back its own spec: the tab's wiring is what is under test, not the list it renders.
vi.mock("../../panels/relationTab", () => ({ relationTab: (spec: unknown) => spec }));
vi.mock("./api", () => ({ unlinkDocument: vi.fn(), linkDocument: vi.fn(), listDocuments: vi.fn() }));
vi.mock("./AssociateDocument", () => ({ AssociateDocument: () => null }));

const mockedUnlink = vi.mocked(unlinkDocument);

interface Phase {
  id: string;
  label: string;
  projectId: string;
  _counts?: { documents?: number | null };
}

const phase: Phase = { id: "5", label: "Phase 1", projectId: "7", _counts: { documents: 3 } };

function buildSpec() {
  return documentsTab<Phase>({
    scopeEntityType: "phase",
    segment: "phases",
    linkField: "phaseIds",
    badge: (entity) => entity._counts?.documents ?? 0,
    projectId: (entity) => entity.projectId,
    entityRef: (entity) => ({ id: entity.id, label: entity.label }),
  }) as unknown as {
    key: string;
    target: string;
    scopeEntityType: string;
    label: string;
    badge: (e: Phase) => number;
    projectId: (e: Phase) => string;
    createPrefill: (e: Phase) => unknown;
    toolbarExtra: (e: Phase, helpers: { organizationId?: number }) => { props: Record<string, unknown> };
    extraRowActions: (e: Phase) => RowActionDef<unknown>[];
  };
}

function context(overrides: Partial<RowActionContext> = {}): RowActionContext {
  return {
    openCreate: vi.fn(),
    confirm: vi.fn((_message, onAccept) => onAccept()),
    refresh: vi.fn(),
    fail: vi.fn(),
    ...overrides,
  };
}

beforeEach(() => {
  mockedUnlink.mockReset();
});

describe("documentsTab", () => {
  it("is the documents of the entity: its badge, its project and a create linked to it", () => {
    const tab = buildSpec();

    expect(tab.key).toBe("documents");
    expect(tab.target).toBe("document");
    expect(tab.scopeEntityType).toBe("phase");
    expect(tab.badge(phase)).toBe(3);
    expect(tab.projectId(phase)).toBe("7");
    expect(tab.createPrefill(phase)).toEqual({ document: { field: "phaseIds", entityType: "phase", ref: { id: "5", label: "Phase 1" } } });
  });

  it("offers « Associer » on the entity's own segment, with the organization and project", () => {
    const element = buildSpec().toolbarExtra(phase, { organizationId: 42 });

    expect(element.props).toMatchObject({ segment: "phases", entityId: "5", organizationId: 42, projectId: "7" });
  });

  it("removes the link once confirmed, then refreshes the lists and badges", async () => {
    mockedUnlink.mockResolvedValue(undefined);
    const ctx = context();
    const [unlink] = buildSpec().extraRowActions(phase);

    unlink.run({ id: "11" }, ctx);
    await Promise.resolve();
    await Promise.resolve();

    expect(ctx.confirm).toHaveBeenCalled();
    expect(mockedUnlink).toHaveBeenCalledWith("phases", "5", "11");
    expect(ctx.refresh).toHaveBeenCalled();
  });

  it("removes nothing before the user confirms", () => {
    const ctx = context({ confirm: vi.fn() });
    const [unlink] = buildSpec().extraRowActions(phase);

    unlink.run({ id: "11" }, ctx);

    expect(mockedUnlink).not.toHaveBeenCalled();
  });

  it("reports a failed removal instead of refreshing", async () => {
    const error = new Error("403");
    mockedUnlink.mockRejectedValue(error);
    const ctx = context();
    const [unlink] = buildSpec().extraRowActions(phase);

    unlink.run({ id: "11" }, ctx);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(ctx.fail).toHaveBeenCalledWith(error, expect.any(String));
    expect(ctx.refresh).not.toHaveBeenCalled();
  });

  it("ignores a row without an id", () => {
    const ctx = context();
    const [unlink] = buildSpec().extraRowActions(phase);

    unlink.run({}, ctx);

    expect(ctx.confirm).not.toHaveBeenCalled();
  });
});
