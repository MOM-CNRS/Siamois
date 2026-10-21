import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { Dropdown } from "primereact/dropdown";
import { createHomeAccess } from "../../api/homeAccesses";
import { queryKeys } from "../../api/queryKeys";
import { messageForError } from "../../api/errors";
import { getAllEntityTypes } from "../../entities/registry";
import { t } from "../../i18n";
import { useTableTypes } from "./useTableTypes";

/**
 * « Créer une nouvelle vue »: a table and one of its types, both required. Creates the view that shows exactly
 * that, and its card on the home page.
 */
export function AddDataAccessDialog({ organizationId, onHide }: { organizationId: number; onHide: () => void }) {
  const queryClient = useQueryClient();
  const tables = getAllEntityTypes().filter((config) => config.list.schema != null);
  const [table, setTable] = useState<string | undefined>(tables[0]?.key);
  const [typeId, setTypeId] = useState<string | null>(null);
  const types = useTableTypes(table, organizationId);

  const selected = tables.find((config) => config.key === table);
  // The view's title says the table briefly (« UE – Couche »): it is read among every view of the organization.
  const tableName = selected?.labels.short ?? selected?.labels.plural ?? "";
  const typeLabel = types.data?.options.find((option) => option.id === typeId)?.label;

  const add = useMutation({
    mutationFn: () => {
      const fieldId = types.data?.fieldId;
      return createHomeAccess({
        organizationId,
        resourceType: table!,
        title: `${tableName} – ${typeLabel}`,
        // The card says the type only.
        label: typeLabel!,
        state: { listFilters: { [fieldId!]: { op: "in", v: [typeId!], labels: { [typeId!]: typeLabel! } } } },
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.homeAccesses(organizationId) });
      // The new view is in the sidebar's views and in the views of the table.
      void queryClient.invalidateQueries({ queryKey: queryKeys.views(organizationId) });
      onHide();
    },
  });

  return (
    <Dialog header={t("home.dataAccess.createView")} visible onHide={onHide} style={{ width: "min(28rem, 95vw)" }} draggable={false}>
      <div className="home-data-access-form">
        <label htmlFor="home-data-access-table">{t("home.dataAccess.table")}</label>
        <Dropdown
          inputId="home-data-access-table"
          value={table}
          options={tables.map((config) => ({ value: config.key, label: config.labels.plural }))}
          onChange={(e) => {
            setTable(e.value as string);
            setTypeId(null);
          }}
        />
        <label htmlFor="home-data-access-type">{t("home.dataAccess.type")}</label>
        <Dropdown
          inputId="home-data-access-type"
          value={typeId}
          placeholder={types.isLoading || (types.data?.options.length ?? 0) > 0 ? undefined : t("home.dataAccess.noType")}
          disabled={types.isLoading || (types.data?.options.length ?? 0) === 0}
          options={(types.data?.options ?? []).map((o) => ({ value: o.id, label: o.label }))}
          onChange={(e) => setTypeId(e.value as string | null)}
          filter
        />
        {add.isError && <small className="home-data-access-error">{messageForError(add.error, t("home.dataAccess.addFailed"))}</small>}
        <div className="home-data-access-actions">
          <Button type="button" text label={t("tabs.cancel")} onClick={onHide} />
          <Button type="button" label={t("home.dataAccess.add")} disabled={table == null || typeId == null || add.isPending} onClick={() => add.mutate()} />
        </div>
      </div>
    </Dialog>
  );
}
