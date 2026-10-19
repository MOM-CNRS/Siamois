import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../../api/queryKeys";
import type { EntityTypeConfig, ListParams, PagedResult } from "../../entities/types";
import type { RowRecord } from "./useEntityListData";

// A parent's children are fetched in one request, up to this many.
const CHILDREN_LIMIT = 200;
// A search opens every branch leading to a match: the fetch is capped so a very broad search
// (every row matches) can't walk the whole hierarchy.
const AUTO_EXPAND_MAX_REQUESTS = 200;

/** The tree view's row: the list's own row plus what `?tree=true` adds. */
export type TreeRow = RowRecord & { hasChildren?: boolean; match?: boolean };

interface TreeNode {
  row: TreeRow;
  // Undefined until the node has been opened once.
  children?: TreeNode[];
}

interface TreeState {
  roots: TreeNode[];
  expanded: Record<string, boolean>;
  total: number;
}

export interface UseTreeRowsOptions {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown> | undefined;
  organizationId?: number;
  // The result set the tree shows (search, sort, filters): any change of it starts over.
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  // The catalog fields on screen, projected into each row like the flat list does.
  fields: string[];
  offset: number;
  limit: number;
  enabled: boolean;
}

/** What the table needs to draw the rows as a hierarchy (EntityTable's `tree` prop). */
export interface EntityTableTree {
  depthOf: (row: RowRecord) => number;
  hasChildren: (row: RowRecord) => boolean;
  isExpanded: (row: RowRecord) => boolean;
  isLoading: (row: RowRecord) => boolean;
  // A parent shown only because a match is below it.
  isContext: (row: RowRecord) => boolean;
  onToggle: (row: RowRecord) => void;
}

const key = (row: RowRecord) => String(row.id);

function toNodes(rows: TreeRow[]): TreeNode[] {
  return rows.map((row) => ({ row }));
}

function updateNode(nodes: TreeNode[], id: string, update: (node: TreeNode) => TreeNode): TreeNode[] {
  return nodes.map((node) => {
    if (key(node.row) === id) return update(node);
    if (node.children) return { ...node, children: updateNode(node.children, id, update) };
    return node;
  });
}

function findNode(nodes: TreeNode[], id: string): TreeNode | undefined {
  for (const node of nodes) {
    if (key(node.row) === id) return node;
    const found = node.children && findNode(node.children, id);
    if (found) return found;
  }
  return undefined;
}

/**
 * The list as a hierarchy, flattened to the rows the table shows: the roots page by page, a parent's
 * children fetched when it is opened — and, while a search or a filter narrows the list, every branch
 * leading to a match already open (the parents that merely lead there are `isContext`). The server
 * sorts the roots and each parent's children the same way, so sorting never breaks the hierarchy.
 * Branches opened by hand stay open when the list is fetched again (a saved cell, a created row).
 */
export function useTreeRows({ entityType, config, organizationId, params, fields, offset, limit, enabled }: UseTreeRowsOptions) {
  const narrowed = Boolean(params.search) || params.filters != null;
  const fieldsParam = fields.length > 0 ? fields.join(",") : undefined;

  const fetchPage = useCallback(
    (extra: Partial<ListParams>) =>
      config!.api.list({ ...params, organizationId, fields: fieldsParam, tree: true, ...extra } as ListParams) as Promise<
        PagedResult<TreeRow>
      >,
    [config, params, organizationId, fieldsParam],
  );
  const fetchChildren = useCallback(
    (parent: TreeRow) =>
      fetchPage({ offset: 0, limit: CHILDREN_LIMIT, scope: { entityType, id: parent.id as string | number, path: "children" } }),
    [fetchPage, entityType],
  );

  // The branches open right now, read by the next fetch of the roots.
  const openRef = useRef<Set<string>>(new Set());

  const query = useQuery({
    queryKey: [...queryKeys.entityList(entityType), "tree", organizationId, params, fieldsParam, offset, limit],
    enabled: enabled && config != null,
    placeholderData: (previous) => previous,
    queryFn: async (): Promise<TreeState> => {
      const page = await fetchPage({ offset, limit });
      const keep = new Set(openRef.current);
      let budget = AUTO_EXPAND_MAX_REQUESTS;
      const expanded: Record<string, boolean> = {};
      const open = async (nodes: TreeNode[]): Promise<TreeNode[]> =>
        Promise.all(
          nodes.map(async (node) => {
            if (!node.row.hasChildren || budget <= 0 || !(narrowed || keep.has(key(node.row)))) return node;
            budget -= 1;
            expanded[key(node.row)] = true;
            return { ...node, children: await open(toNodes((await fetchChildren(node.row)).data)) };
          }),
        );
      return { roots: await open(toNodes(page.data)), expanded, total: page.totalCount };
    },
  });

  // What was fetched, plus what the user opened since.
  const [tree, setTree] = useState<TreeState | null>(null);
  useEffect(() => {
    if (query.data) setTree(query.data);
  }, [query.data]);
  useEffect(() => {
    openRef.current = new Set(Object.keys(tree?.expanded ?? {}).filter((id) => tree?.expanded[id]));
  }, [tree]);

  const [loading, setLoading] = useState<Set<string>>(new Set());

  const onToggle = useCallback(
    async (row: RowRecord) => {
      const id = key(row);
      const current = tree;
      if (!current) return;
      if (current.expanded[id]) {
        setTree({ ...current, expanded: { ...current.expanded, [id]: false } });
        return;
      }
      const node = findNode(current.roots, id);
      if (node?.children) {
        setTree({ ...current, expanded: { ...current.expanded, [id]: true } });
        return;
      }
      setLoading((ids) => new Set(ids).add(id));
      try {
        const children = toNodes((await fetchChildren(row as TreeRow)).data);
        setTree((latest) =>
          latest
            ? {
                ...latest,
                roots: updateNode(latest.roots, id, (n) => ({ ...n, children })),
                expanded: { ...latest.expanded, [id]: true },
              }
            : latest,
        );
      } finally {
        setLoading((ids) => {
          const next = new Set(ids);
          next.delete(id);
          return next;
        });
      }
    },
    [tree, fetchChildren],
  );

  const flat = useMemo(() => {
    const rows: TreeRow[] = [];
    const depths = new Map<string, number>();
    const walk = (nodes: TreeNode[], depth: number) => {
      for (const node of nodes) {
        rows.push(node.row);
        depths.set(key(node.row), depth);
        if (node.children && tree?.expanded[key(node.row)]) walk(node.children, depth + 1);
      }
    };
    if (tree) walk(tree.roots, 0);
    return { rows, depths };
  }, [tree]);

  const tableTree: EntityTableTree = useMemo(
    () => ({
      depthOf: (row) => flat.depths.get(key(row)) ?? 0,
      hasChildren: (row) => (row as TreeRow).hasChildren === true,
      isExpanded: (row) => tree?.expanded[key(row)] === true,
      isLoading: (row) => loading.has(key(row)),
      isContext: (row) => narrowed && (row as TreeRow).match === false,
      onToggle: (row) => void onToggle(row),
    }),
    [flat, tree, loading, narrowed, onToggle],
  );

  return {
    rows: flat.rows as RowRecord[],
    totalCount: tree?.total ?? 0,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    error: query.error,
    tree: tableTree,
  };
}
