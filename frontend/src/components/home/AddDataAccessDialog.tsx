import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { createHomeAccess } from "../../api/homeAccesses";
import { queryKeys } from "../../api/queryKeys";
import { messageForError } from "../../api/errors";
import { getAllEntityTypes } from "../../entities/registry";
import { t } from "../../i18n";
import { useTableTypes } from "./useTableTypes";
import { useToastOnChange } from "../../notify/NotifyProvider";

/**
 * « Créer une nouvelle vue »: a table and one of its types, both required, and the view's name (« Table – Type »
 * until the user writes another). Creates the view that shows exactly that, and its card on the home page.
 */
export function AddDataAccessDialog({ organizationId, onHide }: { organizationId: number; onHide: () => void }) {
  const queryClient = useQueryClient();
  const tables = getAllEntityTypes().filter((config) => config.list.schema != null);
  const [table, setTable] = useState<string | undefined>(tables[0]?.key);
  const [typeId, setTypeId] = useState<string | null>(null);
  // What the user typed; null while the name follows the table and type.
  const [typedName, setTypedName] = useState<string | null>(null);
  const types = useTableTypes(table, organizationId);

  const selected = tables.find((config) => config.key === table);
  // The default name says the table briefly (« UE – Couche »): it is read among every view of the organization.
  const tableName = selected?.labels.short ?? selected?.labels.plural ?? "";
  const typeLabel = types.data?.options.find((option) => option.id === typeId)?.label;
  const defaultName = typeLabel ? `${tableName} – ${typeLabel}` : "";
  const name = typedName ?? defaultName;

  const add = useMutation({
    mutationFn: () => {
      const fieldId = types.data?.fieldId;
      return createHomeAccess({
        organizationId,
        resourceType: table!,
        // The card shows the view's name.
        title: name.trim(),
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

  useToastOnChange(add.isError ? messageForError(add.error, t("home.dataAccess.addFailed")) : null);
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
        <label htmlFor="home-data-access-name">{t("home.dataAccess.name")}</label>
        <InputText
          id="home-data-access-name"
          value={name}
          maxLength={200}
          onChange={(e) => setTypedName(e.target.value)}
        />
        <div className="home-data-access-actions">
          <Button type="button" text label={t("tabs.cancel")} onClick={onHide} />
          <Button type="button" label={t("home.dataAccess.add")} disabled={table == null || typeId == null || name.trim() === "" || add.isPending} onClick={() => add.mutate()} />
        </div>
      </div>
    </Dialog>
  );
}
