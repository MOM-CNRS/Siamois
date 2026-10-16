import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { queryKeys } from "../api/queryKeys";
import { fetchTypesCatalog, type TypesCatalogBody } from "../entities/typeCatalog";
import { parseLayout } from "../fields/layout";
import type { FieldRules, RuledColumn } from "../rules";

/**
 * The conditional rules of a list's cells, per (project, type). They belong to the configuration of
 * a type in a project, so a list mixing several projects and types can't read them off one catalog:
 * each row is evaluated with the rules of its own project's form for its own type. They come from the
 * layouts the project's types endpoint already serves (GET /api/v1/projects/{id}/<segment> — the form
 * of each type, rules on its columns), fetched once per project present in the rows — the same
 * cached catalog the fiches and create forms read.
 */
export interface TypeRules {
  /** The rules of a field for the row's (project, type); undefined while they are not loaded or there are none. */
  rulesOf(row: RowTypeRef, fieldId: string): FieldRules | null | undefined;
  /** Every loaded form's columns, to work out which other fields the rules read. */
  columnSets: RuledColumn[][];
}

export interface RowTypeRef {
  projectId?: string | null;
  type?: { id: string } | null;
}

const DEFAULT_KEY = "_untyped";

type FormRules = Map<string, FieldRules | null | undefined>;
type ProjectRules = Map<string, FormRules>;

function rulesOfLayout(layoutJson: string | undefined): FormRules {
  const out: FormRules = new Map();
  for (const panel of parseLayout(layoutJson ?? "")) {
    for (const row of panel.rows) {
      for (const col of row.columns) {
        if (col.fieldId != null) out.set(String(col.fieldId), col.rules);
      }
    }
  }
  return out;
}

function projectRulesOf(body: TypesCatalogBody): ProjectRules {
  const out: ProjectRules = new Map();
  // an entity with no type reads the rules of the table's first type
  out.set(DEFAULT_KEY, rulesOfLayout(body.data?.[0]?.formBundle?.layoutJson));
  for (const type of body.data ?? []) out.set(type.id, rulesOfLayout(type.formBundle?.layoutJson));
  return out;
}

/** The projects the given rows belong to, sorted, as a stable list (same content, same array). */
export function useProjectIdsOf(rows: readonly RowTypeRef[]): string[] {
  const key = [...new Set(rows.map((r) => r.projectId).filter((id): id is string => id != null))].sort().join(",");
  return useMemo(() => (key ? key.split(",") : []), [key]);
}

/**
 * @param projectIds the projects whose forms to load: those of the rows fetched so far (the list asks
 *                   for the fields the rules read only once it knows which projects it shows)
 */
export function useTypeRules(segment: string | undefined, projectIds: readonly string[]): TypeRules | undefined {
  const queries = useQueries({
    queries: projectIds.map((projectId) => ({
      queryKey: queryKeys.typeRules(segment, projectId),
      queryFn: () => fetchTypesCatalog(`/api/v1/projects/${projectId}/${segment}`),
      select: projectRulesOf,
      staleTime: Infinity,
      enabled: segment != null,
    })),
  });

  // useQueries returns a fresh array each render; what matters is which data arrived.
  const loaded = queries.map((q) => q.dataUpdatedAt).join(",");
  return useMemo(() => {
    if (!segment) return undefined;
    const byProject = new Map<string, ProjectRules>();
    projectIds.forEach((id, i) => {
      const data = queries[i]?.data;
      if (data) byProject.set(id, data);
    });
    const columnSets: RuledColumn[][] = [];
    byProject.forEach((project) =>
      project.forEach((form) =>
        columnSets.push([...form.entries()].map(([fieldId, rules]) => ({ fieldId, rules }))),
      ),
    );
    return {
      columnSets,
      rulesOf(row: RowTypeRef, fieldId: string) {
        const project = row.projectId != null ? byProject.get(row.projectId) : undefined;
        if (!project) return undefined;
        const form = (row.type?.id != null ? project.get(row.type.id) : undefined) ?? project.get(DEFAULT_KEY);
        return form?.get(fieldId);
      },
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [segment, projectIds, loaded]);
}
