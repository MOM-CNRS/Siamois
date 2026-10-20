import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { deleteFicheConfig, putFicheConfig, type FicheConfigScope } from "../api/ficheConfigs";
import type { FicheScope } from "./ficheConfig";
import { toConfigTabs, type EditableTab } from "./ficheTabsModel";

/**
 * Saves (or gives up) the tabs of a fiche's project. Saved for the fiche's sub-type alone, or for every
 * sub-type of the type; either way every cached configuration is dropped, since another fiche may now read this one.
 */
export function useFicheTabsSave(entityType: string, organizationId: number, scope: FicheScope) {
  const queryClient = useQueryClient();
  const target = useCallback(
    (subTypeOnly: boolean): FicheConfigScope => ({
      organizationId,
      resourceType: entityType,
      projectId: scope.projectId,
      typeId: subTypeOnly ? scope.typeId : undefined,
    }),
    [organizationId, entityType, scope.projectId, scope.typeId],
  );
  const refresh = useCallback(
    () => queryClient.invalidateQueries({ queryKey: ["fiche-config", organizationId] }),
    [queryClient, organizationId],
  );
  const save = useCallback(
    async (rows: EditableTab[], subTypeOnly: boolean) => {
      await putFicheConfig(target(subTypeOnly), toConfigTabs(rows));
      await refresh();
    },
    [target, refresh],
  );
  const reset = useCallback(
    async (subTypeOnly: boolean) => {
      await deleteFicheConfig(target(subTypeOnly));
      await refresh();
    },
    [target, refresh],
  );
  return { save, reset };
}
