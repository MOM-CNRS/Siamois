// Which descendants a structure duplication includes. The server copies a node only when its parent
// is copied too (a copy is attached to the copy of its parent), so a selection is always kept
// closed: checking a node checks the path down to it (its ancestors, without which it would have no
// parent copy to sit under) and nothing below it, unchecking one unchecks everything under it. What
// the summary counts is then exactly what gets created.

import type { DuplicationNode } from "../entities/types";

export interface StructureIndex {
  childrenOf: Map<string, string[]>;
  parentOf: Map<string, string | null>;
}

const key = (id: string | number) => String(id);

export function indexStructure(descendants: readonly DuplicationNode[], rootId: string | number): StructureIndex {
  const childrenOf = new Map<string, string[]>();
  const parentOf = new Map<string, string | null>();
  for (const node of descendants) {
    const parent = node.parentId != null ? key(node.parentId) : key(rootId);
    parentOf.set(key(node.id), parent);
    childrenOf.set(parent, [...(childrenOf.get(parent) ?? []), key(node.id)]);
  }
  return { childrenOf, parentOf };
}

function withDescendants(index: StructureIndex, id: string, into: Set<string>) {
  into.add(id);
  for (const child of index.childrenOf.get(id) ?? []) withDescendants(index, child, into);
}

/** `id` checked: it, and the nodes above it up to the root. Its own descendants stay as they are. */
export function check(index: StructureIndex, selected: ReadonlySet<string>, rootId: string | number, id: string): Set<string> {
  const next = new Set(selected).add(id);
  for (let parent = index.parentOf.get(id); parent != null && parent !== key(rootId); parent = index.parentOf.get(parent)) {
    next.add(parent);
  }
  return next;
}

/** `id` unchecked: it and everything under it. */
export function uncheck(index: StructureIndex, selected: ReadonlySet<string>, id: string): Set<string> {
  const removed = new Set<string>();
  withDescendants(index, id, removed);
  return new Set([...selected].filter((s) => !removed.has(s)));
}

export function selectAll(descendants: readonly DuplicationNode[]): Set<string> {
  return new Set(descendants.map((n) => key(n.id)));
}

/** The units created: the root and the selected descendants, times the number of copies. */
export function createdCount(selectedDescendants: number, copies: number): number {
  return (1 + selectedDescendants) * Math.max(1, copies);
}
