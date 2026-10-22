import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Dropdown } from "primereact/dropdown";
import { createHomeAccess } from "../../api/resources/homeAccesses";
import { listViews } from "../../api/resources/views";
import { queryKeys } from "../../api/queryKeys";
import { messageForError } from "../../api/errors";
import { getAllEntityTypes } from "../../entities/registry";
import { t } from "../../i18n";
import { useToastOnChange } from "../../notify/NotifyProvider";

/**
 * The last slot of the featured views, in edit mode: a transparent card holding the search of the organization's
 * views — picking one features it at once. Its footer, « Créer une nouvelle vue », hands over to the creation dialog.
 */
export function AddViewCard({ organizationId, onCreate }: { organizationId: number; onCreate: () => void }) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const views = useQuery({
    queryKey: [...queryKeys.views(organizationId), "pick", search],
    queryFn: () => listViews(organizationId, { q: search || undefined, limit: 50 }),
    staleTime: 0,
  });
  const add = useMutation({
    mutationFn: (viewId: number) => createHomeAccess({ organizationId, viewId }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.homeAccesses(organizationId) }),
  });
  const tableName = (key: string) => getAllEntityTypes().find((config) => config.key === key)?.labels.plural ?? key;

  useToastOnChange(add.isError ? messageForError(add.error, t("home.dataAccess.addFailed")) : null);
  return (
    <div className="home-data-access-add">
      <Dropdown
        value={null}
        options={(views.data?.data ?? []).map((v) => ({ value: v.id, label: v.title, table: tableName(v.resourceType) }))}
        optionLabel="label"
        itemTemplate={(o: { label: string; table: string }) => (
          <span className="home-data-access-view-item">
            <span className="home-data-access-view-title">{o.label}</span>
            <small className="home-data-access-view-table">{o.table}</small>
          </span>
        )}
        placeholder={t("home.dataAccess.addButton")}
        emptyFilterMessage={t("home.dataAccess.noView")}
        emptyMessage={t("home.dataAccess.noView")}
        className="home-data-access-dropdown"
        panelClassName="home-data-access-panel"
        disabled={add.isPending}
        onChange={(e) => add.mutate(e.value as number)}
        // The search goes to the server: the organization may have more views than one page.
        filter
        onFilter={(e) => setSearch(e.filter)}
        panelFooterTemplate={
          <div className="home-data-access-create-footer">
            <button type="button" className="home-data-access-create" onClick={onCreate}>
              <i className="bi bi-plus-lg" />
              {t("home.dataAccess.createView")}
            </button>
          </div>
        }
      />
    </div>
  );
}
