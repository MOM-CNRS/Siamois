import { useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "../../api/client";
import { messageForError } from "../../api/errors";
import { queryKeys } from "../../api/queryKeys";
import { DeleteEntityDialog } from "../../components/detail/DeleteEntityDialog";
import type { EntityTypeConfig } from "../../entities/shared/types";
import { t } from "../../i18n";
import { useNotify } from "../../notify/NotifyProvider";
import { announceEntityDeleted } from "./entityDeleted";

interface DeleteTarget {
  id: string | number;
  // The name to type to confirm (only when the entity type requires it).
  name?: string;
}

/**
 * The permanent deletion of an entity, from a list row or a fiche: `requestDelete` opens the
 * confirmation, and the dialog (to render once) stays open with the server's message when the
 * entity still has relationships. On success the lists are refreshed, the deleted fiche is dropped
 * from the cache and the panes showing it let go of it.
 */
export function useDeleteEntity(
  entityType: string,
  config: EntityTypeConfig<unknown, unknown> | undefined,
  organizationId?: number,
): { requestDelete: (target: DeleteTarget) => void; dialog: ReactNode; canDelete: boolean } {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [target, setTarget] = useState<DeleteTarget | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (id: string | number) => config!.api.delete!(id),
    onSuccess: (_result, id) => {
      setTarget(null);
      queryClient.removeQueries({ queryKey: queryKeys.entityDetail(entityType, id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityLists() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetails() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.organizationCounts(organizationId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.projectHomeRecent(organizationId) });
      notify.success(t("delete.success"));
      announceEntityDeleted(entityType, id);
    },
    onError: (err: unknown) => {
      // A refusal (409) is explained in the dialog; anything else is a failure to report.
      if (err instanceof ApiError && err.status === 409) {
        setError(err.message || t("delete.hasRelations"));
      } else {
        setError(messageForError(err, t("delete.failed")));
      }
    },
  });

  const canDelete = config?.api.delete != null;

  const dialog = (
    <DeleteEntityDialog
      visible={target != null}
      confirmName={config?.deleteRequiresName ? (target?.name ?? "") : undefined}
      error={error}
      busy={mutation.isPending}
      onConfirm={() => target && mutation.mutate(target.id)}
      onHide={() => {
        if (!mutation.isPending) setTarget(null);
      }}
    />
  );

  return {
    canDelete,
    dialog,
    requestDelete: (next) => {
      setError(null);
      mutation.reset();
      setTarget(next);
    },
  };
}
