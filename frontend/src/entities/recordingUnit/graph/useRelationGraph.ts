import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../../../api/queryKeys";
import { getRelationGraph } from "./api";

export function useRelationGraph(id: string | number) {
  return useQuery({
    queryKey: queryKeys.relationGraph(id),
    queryFn: () => getRelationGraph(id),
  });
}
