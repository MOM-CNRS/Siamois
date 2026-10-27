import {describe, expect, it} from "vitest";
import {layoutGraph, type LayoutLabels, NODE_HEIGHT} from "./layout";
import type {GraphUnitRef, GraphZone, RelationGraph} from "./types";

const labels: LayoutLabels = {
  zone: { parents: "P", children: "C", anterior: "A", synchronous: "S", posterior: "N", others: "O" },
  notApplicable: "n/a",
  more: (count) => `+${count}`,
};

const ref = (id: string, extra: Partial<GraphUnitRef> = {}): GraphUnitRef => ({ resourceId: id, resourceType: "recording-units", label: `US ${id}`, ...extra });
const zone = (values: GraphUnitRef[], total = values.length): GraphZone => ({ values, total, complete: total === values.length });
const empty = zone([]);

function graph(over: Partial<RelationGraph>): RelationGraph {
  return { center: ref("42"), parents: empty, children: empty, anterior: empty, synchronous: empty, posterior: empty, others: empty, fields: { anterior: "-328", synchronous: "-330", posterior: "-329" }, ...over };
}

describe("layoutGraph", () => {
  it("puts the unit in the middle, parents above, children below", () => {
    const { nodes } = layoutGraph(graph({ parents: zone([ref("1")]), children: zone([ref("2")]) }), labels);
    const center = nodes.find((n) => n.kind === "center")!;
    const parent = nodes.find((n) => n.id === "parents:1")!;
    const child = nodes.find((n) => n.id === "children:2")!;
    expect(parent.y).toBeLessThan(center.y);
    expect(child.y).toBeGreaterThan(center.y);
  });

  it("centers a row of units on the unit", () => {
    const { nodes } = layoutGraph(graph({ parents: zone([ref("1"), ref("2"), ref("3")]) }), labels);
    const xs = ["parents:1", "parents:2", "parents:3"].map((id) => nodes.find((n) => n.id === id)!.x);
    expect(xs[1]).toBeCloseTo(-85);
    expect(xs[0]).toBeLessThan(xs[1]);
    expect(xs[2]).toBeGreaterThan(xs[1]);
  });

  it("wraps a long row so it stays clear of the right column", () => {
    const children = Array.from({ length: 7 }, (_, i) => ref(`c${i}`));
    const { nodes } = layoutGraph(graph({ children: zone(children) }), labels);
    const units = nodes.filter((n) => n.zone === "children" && n.kind === "unit");
    expect(Math.max(...units.map((n) => n.x + n.width))).toBeLessThan(330);
    expect(new Set(units.map((n) => n.y)).size).toBe(3);
  });

  it("aligns the synchronous units with the center, the more recent above and the older below", () => {
    const { nodes } = layoutGraph(graph({ anterior: zone([ref("3")]), synchronous: zone([ref("4")]), posterior: zone([ref("5")]) }), labels);
    const find = (id: string) => nodes.find((n) => n.id === id)!;
    const center = find("unit:42");
    const sync = find("synchronous:4");
    expect(sync.y + sync.height / 2).toBeCloseTo(center.y + center.height / 2);
    expect(find("posterior:5").y + NODE_HEIGHT).toBeLessThan(sync.y);
    expect(find("anterior:3").y).toBeGreaterThan(sync.y + NODE_HEIGHT);
    expect(find("anterior:3").x).toBeGreaterThan(0);
    const others = find("others:empty");
    expect(others.x).toBeLessThan(0);
    expect(others.label).toBe("n/a");
  });

  it("centers several synchronous units on the center's level", () => {
    const { nodes } = layoutGraph(graph({ synchronous: zone([ref("4"), ref("5"), ref("6")]) }), labels);
    const ys = ["4", "5", "6"].map((id) => nodes.find((n) => n.id === `synchronous:${id}`)!.y + NODE_HEIGHT / 2);
    expect(ys[1]).toBeCloseTo(0);
    expect(ys[0] + ys[2]).toBeCloseTo(0);
  });

  it("keeps the title of an empty zone, with its add button, only when the caller can write", () => {
    const readOnly = layoutGraph(graph({}), labels).nodes.filter((n) => n.kind === "zone");
    expect(readOnly.map((n) => n.zone)).toEqual(["others"]);
    const writable = layoutGraph(graph({}), labels, { editable: true }).nodes.filter((n) => n.kind === "zone");
    expect(writable.map((n) => n.zone).sort()).toEqual(["anterior", "children", "others", "parents", "posterior", "synchronous"]);
    expect(writable.find((n) => n.zone === "parents")!.addable).toBe(true);
    expect(writable.find((n) => n.zone === "others")!.addable).toBe(false);
  });

  it("adds a +N tile for the units a zone's preview doesn't carry", () => {
    const { nodes } = layoutGraph(graph({ children: zone([ref("2")], 12) }), labels);
    expect(nodes.find((n) => n.id === "more:children")!.label).toBe("+11");
  });

  it("labels a stratigraphic edge with its type and flags the uncertain ones", () => {
    const { edges } = layoutGraph(graph({
      anterior: zone([ref("3", { qualifier: { concept: { label: "recoupe" }, uncertain: true } })]),
    }), labels);
    expect(edges).toHaveLength(1);
    expect(edges[0].label).toBe("recoupe ?");
    expect(edges[0].dashed).toBe(true);
  });

  it("draws no title for an empty parents or children zone a reader cannot add to", () => {
    const { nodes } = layoutGraph(graph({}), labels);
    expect(nodes.some((n) => n.id === "zone:parents")).toBe(false);
  });

  it("without its lines, draws one arrow to the title of each zone that has units", () => {
    const { edges, nodes } = layoutGraph(graph({ parents: zone([ref("1")]), anterior: zone([ref("3"), ref("4")]) }), labels, { showLinks: false });
    expect(edges.map((e) => e.target).sort()).toEqual(["zone:anterior", "zone:parents"]);
    expect(edges.every((e) => e.source === "unit:42")).toBe(true);
    // The parents' title moves between the center and its units, where the arrow lands.
    const title = nodes.find((n) => n.id === "zone:parents")!;
    const parent = nodes.find((n) => n.id === "parents:1")!;
    expect(title.y).toBeGreaterThan(parent.y);
  });

  it("without its lines, lays a stratigraphic zone out as a grid instead of a stack", () => {
    const units = Array.from({ length: 6 }, (_, i) => ref(`a${i}`));
    const stacked = layoutGraph(graph({ anterior: zone(units) }), labels).nodes.filter((n) => n.zone === "anterior" && n.kind === "unit");
    expect(new Set(stacked.map((n) => n.x)).size).toBe(1);
    const grid = layoutGraph(graph({ anterior: zone(units) }), labels, { showLinks: false }).nodes.filter((n) => n.zone === "anterior" && n.kind === "unit");
    expect(new Set(grid.map((n) => n.x)).size).toBe(3);
    expect(new Set(grid.map((n) => n.y)).size).toBe(2);
  });

  it("keeps the synchronous grid centered on the center's level", () => {
    const units = Array.from({ length: 4 }, (_, i) => ref(`s${i}`));
    const { nodes } = layoutGraph(graph({ synchronous: zone(units) }), labels, { showLinks: false });
    const ys = nodes.filter((n) => n.zone === "synchronous" && n.kind === "unit").map((n) => n.y + NODE_HEIGHT / 2);
    expect(Math.min(...ys) + Math.max(...ys)).toBeCloseTo(0);
  });

  describe("zone frames", () => {
    it("draws one frame per filled zone, behind the tiles, never around the center", () => {
      const { nodes } = layoutGraph(graph({ parents: zone([ref("1")]), posterior: zone([ref("2")]) }), labels);
      const frames = nodes.filter((n) => n.kind === "frame");
      expect(frames.map((f) => f.zone).sort()).toEqual(["others", "parents", "posterior"]);
      // the frames come first, so they sit under everything else
      expect(nodes.slice(0, frames.length).every((n) => n.kind === "frame")).toBe(true);
    });

    it("contains the title, the units and the +N tile of its zone", () => {
      const { nodes } = layoutGraph(graph({ anterior: zone([ref("1"), ref("2")], 5) }), labels);
      const frame = nodes.find((n) => n.id === "frame:anterior")!;
      const members = nodes.filter((n) => n.zone === "anterior" && n.kind !== "frame");
      expect(members.length).toBe(4); // title, two units, "+3"
      for (const m of members) {
        expect(m.x).toBeGreaterThan(frame.x);
        expect(m.y).toBeGreaterThan(frame.y);
        expect(m.x + m.width).toBeLessThan(frame.x + frame.width);
        expect(m.y + m.height).toBeLessThan(frame.y + frame.height);
      }
    });

    it("frames an empty zone only for an editor, and fainter", () => {
      const readOnly = layoutGraph(graph({}), labels).nodes.filter((n) => n.kind === "frame");
      expect(readOnly.map((f) => f.zone)).toEqual(["others"]);
      const editable = layoutGraph(graph({}), labels, { editable: true }).nodes.filter((n) => n.kind === "frame");
      expect(editable.length).toBe(6);
      expect(editable.every((f) => f.muted)).toBe(true);
    });

    it("does not change where the tiles are", () => {
      const g = graph({ parents: zone([ref("1")]), synchronous: zone([ref("2")]) });
      const withFrames = layoutGraph(g, labels).nodes.find((n) => n.id === "parents:1")!;
      expect(withFrames.y).toBe(-190 - NODE_HEIGHT / 2);
    });
  });
});
