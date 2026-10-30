import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../../api/queryKeys";
import {getRelationGraph} from "./api";

// The whole graph, with the relations between its units: the pointer shows them.
export function useRelationGraph(id: string | number) {
  return useQuery({
    queryKey: queryKeys.relationGraph(id),
    queryFn: () => getRelationGraph(id, true),
  });
}
