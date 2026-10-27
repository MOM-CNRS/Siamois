import {useRef, useState} from "react";
import {Button} from "primereact/button";
import {Dialog} from "primereact/dialog";
import {Dropdown} from "primereact/dropdown";
import {InputText} from "primereact/inputtext";
import type {FilterValue} from "../../entities/shared/types";
import {t} from "../../i18n";
import {EntityListPanel} from "../EntityListPanel";
import {type EditableTab, newListTab, targetLabel} from "./ficheTabsModel";

export interface AddListTabDialogProps {
  // The fiche the tab is for: what the preview list is scoped to.
  entityType: string;
  entityId: string | number;
  projectId?: string | number;
  organizationId: number;
  // The types the fiche may list (its relation tabs' targets).
  targets: string[];
  // A list the user is already looking at, kept as a tab.
  initial?: { target: string; filters: Record<string, FilterValue> };
  onAdd: (tab: EditableTab) => void;
  onHide: () => void;
}

/**
 * « Ajouter un onglet de liste » (and « Enregistrer comme onglet… », which opens it on the list being looked at):
 * pick what the tab lists, filter the live list, name it. The filters set here become the tab's own, fixed;
 * whoever opens the tab can add theirs on top.
 */
export function AddListTabDialog({ entityType, entityId, projectId, organizationId, targets, initial, onAdd, onHide }: AddListTabDialogProps) {
  const [target, setTarget] = useState(initial?.target ?? targets[0]);
  const [label, setLabel] = useState("");
  const filters = useRef<Record<string, FilterValue>>(initial?.filters ?? {});
  // The filters of the target the dialog opened on; another target starts clean.
  const startFilters = target === initial?.target ? initial.filters : undefined;

  function changeTarget(next: string) {
    filters.current = {};
    setTarget(next);
  }

  return (
    <Dialog header={t("tabs.addTitle")} visible onHide={onHide} style={{ width: "min(64rem, 95vw)" }} draggable={false}>
      <div className="fiche-tabs-add">
        {targets.length > 1 && (
          <div className="fiche-tabs-add-field">
            <label htmlFor="fiche-tabs-add-target">{t("tabs.targetLabel")}</label>
            <Dropdown
              inputId="fiche-tabs-add-target"
              value={target}
              options={targets.map((value) => ({ value, label: targetLabel(value) }))}
              onChange={(e) => changeTarget(e.value as string)}
            />
          </div>
        )}
        <div className="fiche-tabs-add-field">
          <label htmlFor="fiche-tabs-add-name">{t("tabs.nameLabel")}</label>
          <InputText id="fiche-tabs-add-name" value={label} maxLength={200} placeholder={targetLabel(target)} onChange={(e) => setLabel(e.target.value)} />
        </div>
        <p className="fiche-tabs-add-hint">{t("tabs.addHint")}</p>
        <div className="fiche-tabs-add-preview">
          <EntityListPanel
            key={target}
            embedded
            volatile
            creatable={false}
            entityType={target}
            organizationId={organizationId}
            scope={{ entityType, id: entityId, projectId }}
            initialFilters={startFilters}
            onFiltersChange={(next) => {
              filters.current = next;
            }}
          />
        </div>
        <div className="fiche-tabs-add-actions">
          <Button type="button" label={t("tabs.cancel")} text onClick={onHide} />
          <Button type="button" label={t("tabs.addConfirm")} onClick={() => onAdd(newListTab(target, label, filters.current))} />
        </div>
      </div>
    </Dialog>
  );
}
