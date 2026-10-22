import { describe, expect, it, vi } from "vitest";
import { toFlow } from "./RelationGraphTab";
import type { GraphUnitRef, GraphZone, RelationGraph } from "./types";

const ref = (id: string): GraphUnitRef => ({ resourceId: id, resourceType: "recording-units", label: `US ${id}` });
const zone = (...ids: string[]): GraphZone => ({ values: ids.map(ref), total: ids.length, complete: true });

const graph: RelationGraph = {
  center: ref("42"),
  parents: zone("1"),
  children: zone("2"),
  anterior: zone("3"),
  synchronous: zone(),
  posterior: zone("4"),
  others: zone(),
  fields: { anterior: "-328", synchronous: "-330", posterior: "-329" },
};
const actions = { open: vi.fn() };

describe("toFlow", () => {
  it("gives a unit's node its relation, and leaves the center and the titles without one", () => {
    const { nodes } = toFlow(graph, actions, false);

    expect(nodes.find((n) => n.id === "parents:1")!.data.ref).toEqual(ref("1"));
    expect(nodes.find((n) => n.id === "unit:42")!.data.ref).toBeUndefined();
    expect(nodes.find((n) => n.id === "zone:anterior")!.data.kind).toBe("zone");
  });

  it("draws hierarchy edges vertically, with an arrow, and stratigraphy edges out to the right", () => {
    const { edges } = toFlow(graph, actions, false);

    const parent = edges.find((e) => e.target === "parents:1")!;
    expect([parent.sourceHandle, parent.targetHandle]).toEqual(["src-top", "tgt-bottom"]);
    expect(parent.markerEnd).toBeDefined();
    const posterior = edges.find((e) => e.target === "posterior:4")!;
    expect([posterior.sourceHandle, posterior.targetHandle]).toEqual(["src-right", "tgt-left"]);
    expect(posterior.markerEnd).toBeUndefined();
  });

  it("without links, draws one arrow per zone that has units, each ending on its title", () => {
    const { edges } = toFlow(graph, actions, false, false);

    expect(edges.map((e) => e.target).sort()).toEqual(["zone:anterior", "zone:children", "zone:parents", "zone:posterior"]);
    expect(edges.every((e) => e.markerEnd !== undefined)).toBe(true);
  });

  it("offers a zone its add button only to a writer", () => {
    expect(toFlow(graph, actions, false).nodes.some((n) => n.data.addable)).toBe(false);
    const writable = toFlow(graph, actions, true).nodes.filter((n) => n.data.addable).map((n) => n.data.zone);
    expect(writable).toContain("synchronous");
    expect(writable).not.toContain("others");
  });
});
