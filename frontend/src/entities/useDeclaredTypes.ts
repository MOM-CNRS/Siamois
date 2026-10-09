import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../api/queryKeys";
import type { FilterOption } from "../fields/optionSources";
import { fetchTypesCatalog } from "./typeCatalog";

/**
 * The types a project has declared for a table (its `<x>-types` catalog), as the options of a type
 * picker: the picker offers exactly these, it does not search the thesaurus by the type field's code.
 * Empty until the catalog is there, and without a project (there is nothing declared to pick from).
 */
export function useDeclaredTypes(typesSegment: string | undefined, projectId: string | number | null | undefined): FilterOption[] {
  const query = useQuery({
    queryKey: queryKeys.typesCatalog(`/api/v1/projects/${projectId}/${typesSegment}`),
    queryFn: () => fetchTypesCatalog(`/api/v1/projects/${projectId}/${typesSegment}`),
    enabled: typesSegment != null && projectId != null,
    staleTime: Infinity,
  });
  return (query.data?.data ?? []).map((type) => ({
    id: String(type.id),
    label: type.concept?.resolvedLabel ?? String(type.id),
  }));
}
