import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { TreeTableExpandedKeysType } from "primereact/treetable";
import type { TreeNode as PrimeTreeNode } from "primereact/treenode";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../../api/queryKeys";
import type { EntityTypeConfig, ListParams, PagedResult } from "../../entities/types";
import type { RowRecord } from "./useEntityListData";

// A parent's children are fetched in one request, up to this many.
const CHILDREN_LIMIT = 200;

/** The tree view's row: the list's own row plus what `?tree=true` adds. */
export type TreeRow = RowRecord & { hasChildren?: boolean; match?: boolean; __treeKey?: string };

// A PrimeReact TreeTable node: `key` is the node's path ("rootId/parentId/id"), so an entity under
// several parents shows once per branch, each occurrence with its own open state; `data` is the
// row. `children` stays undefined until the node has been opened once.
type TreeNode = PrimeTreeNode & { data: TreeRow };

interface TreeState {
  roots: TreeNode[];
  expanded: TreeTableExpandedKeysType;
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

/** What the table needs to draw the rows as a TreeTable (EntityTable's `tree` prop). */
export interface EntityTableTree {
  nodes: PrimeTreeNode[];
  expandedKeys: TreeTableExpandedKeysType;
  onToggle: (keys: TreeTableExpandedKeysType) => void;
  // A node is opened: its children are fetched the first time.
  onExpand: (node: PrimeTreeNode) => void;
  // A parent shown only because a match is below it.
  isContext: (row: RowRecord) => boolean;
}

function toNodes(rows: TreeRow[], parentPath?: string): TreeNode[] {
  return rows.map((row) => {
    const key = parentPath == null ? String(row.id) : `${parentPath}/${row.id}`;
    return { key, data: { ...row, __treeKey: key }, leaf: row.hasChildren !== true };
  });
}

function updateNode(nodes: TreeNode[], path: string, update: (node: TreeNode) => TreeNode): TreeNode[] {
  return nodes.map((node) => {
    if (node.key === path) return update(node);
    if (node.children && path.startsWith(`${node.key}/`)) return { ...node, children: updateNode(node.children as TreeNode[], path, update) };
    return node;
  });
}

function findNode(nodes: TreeNode[], path: string): TreeNode | undefined {
  for (const node of nodes) {
    if (node.key === path) return node;
    const found = node.children && path.startsWith(`${node.key}/`) ? findNode(node.children as TreeNode[], path) : undefined;
    if (found) return found;
  }
  return undefined;
}

/**
 * The list as a hierarchy, as the nodes of a TreeTable: the roots page by page, a parent's
 * children fetched when it is opened. A search or a filter narrows what each level holds (the parents
 * that merely lead to a match are `isContext`) but never opens a branch. The server sorts the roots
 * and each parent's children the same way, so sorting never breaks the hierarchy. Branches opened by
 * hand stay open when the list is fetched again (a saved cell, a sort, a search). An entity under
 * several parents appears in each branch; `matchCount` counts it once.
 */
export function useTreeRows({ entityType, config, organizationId, params, fields, offset, limit, enabled }: UseTreeRowsOptions) {
  const narrowed = Boolean(params.search) || Object.keys(params.filters ?? {}).length > 0;
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
      const expanded: TreeTableExpandedKeysType = {};
      const open = async (nodes: TreeNode[]): Promise<TreeNode[]> =>
        Promise.all(
          nodes.map(async (node) => {
            if (node.leaf || !keep.has(node.key as string)) return node;
            expanded[node.key as string] = true;
            return { ...node, children: await open(toNodes((await fetchChildren(node.data)).data, node.key as string)) };
          }),
        );
      return { roots: await open(toNodes(page.data)), expanded, total: page.totalCount };
    },
  });

  // The entities that match, each counted once however many branches show it: the flat list's total
  // for the same search and filters (the tree's own total counts roots, for the paging).
  const matches = useQuery({
    queryKey: [...queryKeys.entityList(entityType), "tree-matches", organizationId, params],
    enabled: enabled && config != null,
    placeholderData: (previous) => previous,
    queryFn: () => config!.api.list({ ...params, organizationId, offset: 0, limit: 1 } as ListParams),
  });

  // What was fetched, plus what the user opened since.
  const [tree, setTree] = useState<TreeState | null>(null);
  useEffect(() => {
    if (query.data) setTree(query.data);
  }, [query.data]);
  useEffect(() => {
    openRef.current = new Set(Object.keys(tree?.expanded ?? {}).filter((id) => tree?.expanded[id]));
  }, [tree]);

  const onToggle = useCallback((keys: TreeTableExpandedKeysType) => {
    setTree((latest) => (latest ? { ...latest, expanded: keys } : latest));
  }, []);

  const onExpand = useCallback(
    async (node: PrimeTreeNode) => {
      const path = node.key as string;
      if (!tree || findNode(tree.roots, path)?.children) return;
      const children = toNodes((await fetchChildren((node as TreeNode).data)).data, path);
      setTree((latest) => (latest ? { ...latest, roots: updateNode(latest.roots, path, (n) => ({ ...n, children })) } : latest));
    },
    [tree, fetchChildren],
  );

  const tableTree: EntityTableTree = useMemo(
    () => ({
      nodes: tree?.roots ?? [],
      expandedKeys: tree?.expanded ?? {},
      onToggle,
      onExpand: (node) => void onExpand(node),
      isContext: (row) => narrowed && (row as TreeRow).match === false,
    }),
    [tree, narrowed, onToggle, onExpand],
  );

  // The rows on screen in display order (the open branches' children included).
  const rows = useMemo(() => {
    const out: RowRecord[] = [];
    const walk = (nodes: TreeNode[]) => {
      for (const node of nodes) {
        out.push(node.data);
        if (node.children && tree?.expanded[node.key as string]) walk(node.children as TreeNode[]);
      }
    };
    if (tree) walk(tree.roots);
    return out;
  }, [tree]);

  return {
    rows,
    totalCount: tree?.total ?? 0,
    matchCount: matches.data?.totalCount ?? 0,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    error: query.error,
    tree: tableTree,
  };
}
