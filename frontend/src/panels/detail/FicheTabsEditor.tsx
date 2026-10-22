import { useRef, useState, type KeyboardEvent } from "react";
import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { Menu } from "primereact/menu";
import { InputText } from "primereact/inputtext";
import type { DetailTabDef } from "../../entities/shared/types";
import { messageForError } from "../../api/errors";
import { t } from "../../i18n";
import { useNotify } from "../../notify/NotifyProvider";
import { AddListTabDialog } from "./AddListTabDialog";
import type { FicheScope } from "./ficheConfig";
import { addTabItems } from "./addTabMenu";
import {
  editableTabs,
  hasVisibleTab,
  moveTab,
  moveTabTo,
  relationTargets,
  removeTab,
  restorableTabs,
  restoreTab,
  shownTabs,
  targetLabel,
  type EditableTab,
} from "./ficheTabsModel";
import { useFicheTabsSave } from "./useFicheTabsSave";

export interface FicheTabsEditorProps {
  entityType: string;
  entityId: string | number;
  organizationId: number;
  scope: FicheScope;
  // The tabs the code declares, and the configuration in force (absent when none).
  codeTabs: DetailTabDef<never>[];
  saved: Record<string, unknown> | undefined;
  // The sub-type the configuration in force was saved for.
  savedTypeId: number | undefined;
  onHide: () => void;
  // The configuration changed: the fiche starts over on its first tab.
  onChanged: () => void;
}

/**
 * « Configurer les onglets » : the tabs of this fiche as the project shows them — order, names, removal (the
 * fiche's own tabs, details and graph, stay) and the list tabs added. Saved for the fiche's sub-type when it has
 * one, else for every one. Nothing is written until « Enregistrer »; « Revenir aux onglets par défaut » removes
 * the configuration in force.
 */
export function FicheTabsEditor({ entityType, entityId, organizationId, scope, codeTabs, saved, savedTypeId, onHide, onChanged }: FicheTabsEditorProps) {
  const notify = useNotify();
  const [rows, setRows] = useState<EditableTab[]>(() => editableTabs(codeTabs, saved));
  const [adding, setAdding] = useState(false);
  const addMenu = useRef<Menu>(null);
  const [busy, setBusy] = useState(false);
  const { save, reset } = useFicheTabsSave(entityType, organizationId, scope);
  const targets = relationTargets(codeTabs);
  // A row is dragged by its handle only (armed on press), so selecting text in the name still works.
  const [armed, setArmed] = useState<string | null>(null);
  const [dragging, setDragging] = useState<string | null>(null);
  const [over, setOver] = useState<string | null>(null);

  function endDrag() {
    setArmed(null);
    setDragging(null);
    setOver(null);
  }

  function onHandleKey(event: KeyboardEvent, key: string) {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    event.preventDefault();
    setRows((r) => moveTab(r, key, event.key === "ArrowUp" ? -1 : 1));
  }

  function rename(key: string, label: string) {
    setRows((current) => current.map((row) => (row.key === key ? { ...row, label } : row)));
  }

  const restorable = restorableTabs(rows);
  const addItems = addTabItems(restorable, targets.length > 0, (key) => setRows((r) => restoreTab(r, key)), () => setAdding(true));

  async function run(action: () => Promise<void>, done: string) {
    setBusy(true);
    try {
      await action();
      notify.success(done);
      onChanged();
      onHide();
    } catch (error) {
      notify.error(messageForError(error, t("tabs.failed")));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Dialog header={t("tabs.editorTitle")} visible onHide={onHide} style={{ width: "min(40rem, 95vw)" }} draggable={false}>
        <div className="fiche-tabs-editor">
          <div className="fiche-tabs-editor-head">
            <p className="fiche-tabs-editor-hint">{t("tabs.editorHint")}</p>
            {addItems.length > 0 && (
              <>
                <Button
                  type="button"
                  icon="bi bi-plus-lg"
                  label={t("tabs.add")}
                  size="small"
                  outlined
                  onClick={(e) => (restorable.length > 0 ? addMenu.current?.toggle(e) : setAdding(true))}
                />
                <Menu ref={addMenu} popup model={addItems} />
              </>
            )}
          </div>
          <ul className="fiche-tabs-editor-rows">
            {shownTabs(rows).map((row) => (
              <li
                key={row.key}
                className={`fiche-tabs-editor-row${dragging === row.key ? " is-dragging" : ""}${over === row.key && dragging !== row.key ? " is-drop-target" : ""}`}
                draggable={armed === row.key}
                onDragStart={(e) => {
                  e.dataTransfer.effectAllowed = "move";
                  e.dataTransfer.setData("text/plain", row.key);
                  setDragging(row.key);
                }}
                onDragOver={(e) => {
                  if (dragging == null) return;
                  e.preventDefault();
                  e.dataTransfer.dropEffect = "move";
                  setOver(row.key);
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  if (dragging != null) setRows((r) => moveTabTo(r, dragging, row.key));
                  endDrag();
                }}
                onDragEnd={endDrag}
              >
                <span
                  className="fiche-tabs-editor-handle"
                  role="button"
                  tabIndex={0}
                  aria-label={t("tabs.dragHandle")}
                  title={t("tabs.dragHandle")}
                  onMouseDown={() => setArmed(row.key)}
                  onMouseUp={() => setArmed(null)}
                  onKeyDown={(e) => onHandleKey(e, row.key)}
                >
                  <i className="bi bi-grip-vertical" />
                </span>
                <InputText
                  className="fiche-tabs-editor-name"
                  value={row.label}
                  maxLength={200}
                  placeholder={row.defaultLabel}
                  aria-label={t("tabs.name")}
                  onChange={(e) => rename(row.key, e.target.value)}
                />
                {row.kind === "list" && <span className="fiche-tabs-editor-kind">{`${t("tabs.listKind")} · ${targetLabel(row.target!)}`}</span>}
                {row.removable ? (
                  <Button icon="bi bi-trash" text rounded size="small" severity="danger" aria-label={t("tabs.remove")} onClick={() => setRows((r) => removeTab(r, row.key))} />
                ) : (
                  <span className="fiche-tabs-editor-remove-spacer" />
                )}
              </li>
            ))}
          </ul>
          {!hasVisibleTab(rows) && <p className="fiche-tabs-editor-warning">{t("tabs.needVisible")}</p>}
          <div className="fiche-tabs-editor-actions">
            <Button type="button" label={t("tabs.reset")} text severity="secondary" disabled={busy || saved == null} onClick={() => void run(() => reset(savedTypeId != null), t("tabs.resetDone"))} />
            <span className="fiche-tabs-editor-spacer" />
            <Button type="button" label={t("tabs.cancel")} text onClick={onHide} />
            <Button type="button" label={t("tabs.save")} disabled={busy || !hasVisibleTab(rows)} onClick={() => void run(() => save(rows, scope.typeId != null), t("tabs.saved"))} />
          </div>
        </div>
      </Dialog>
      {adding && (
        <AddListTabDialog
          entityType={entityType}
          entityId={entityId}
          projectId={scope.projectId}
          organizationId={organizationId}
          targets={targets}
          onHide={() => setAdding(false)}
          onAdd={(tab) => {
            setRows((r) => [...r, tab]);
            setAdding(false);
          }}
        />
      )}
    </>
  );
}
