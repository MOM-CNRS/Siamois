import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../api/queryKeys";
import {type FilterOption, toConceptItemInfo} from "../../fields/optionSources";
import {fetchTypesCatalog, type TypeFormBody} from "./typeCatalog";

/**
 * The types a project has declared for a table (its `<x>-types` catalog), as the options of a type
 * picker: the picker offers exactly these, it does not search the thesaurus by the type field's code.
 * Each option carries what a concept suggestion shows (link to the thesaurus, alt labels…), as in
 * any other concept picker. Empty until the catalog is there, and without a project (there is nothing declared to pick from).
 */
export function useDeclaredTypes(typesSegment: string | undefined, projectId: string | number | null | undefined): FilterOption[] {
  return useDeclaredTypesState(typesSegment, projectId).options;
}

/** {@link useDeclaredTypes} that also says whether the catalog has arrived: "none declared" is not "not loaded yet". */
export function useDeclaredTypesState(
  typesSegment: string | undefined,
  projectId: string | number | null | undefined,
): { options: FilterOption[]; loaded: boolean } {
  const query = useQuery({
    queryKey: queryKeys.typesCatalog(`/api/v1/projects/${projectId}/${typesSegment}`),
    queryFn: () => fetchTypesCatalog(`/api/v1/projects/${projectId}/${typesSegment}`),
    enabled: typesSegment != null && projectId != null,
    staleTime: Infinity,
  });
  const options = (query.data?.data ?? []).map(declaredTypeOption);
  return { options, loaded: query.isSuccess };
}

/** A declared type as a picker option: its concept's label and what a concept suggestion shows of it. */
export function declaredTypeOption(type: TypeFormBody & { id: string }): FilterOption {
  const option: FilterOption = { id: String(type.id), label: type.concept?.resolvedLabel ?? String(type.id) };
  const concept = type.concept ? toConceptItemInfo(type.concept) : undefined;
  if (concept) option.concept = concept;
  return option;
}
