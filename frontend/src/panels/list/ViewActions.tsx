import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { InputText } from "primereact/inputtext";
import { SplitButton } from "primereact/splitbutton";
import { createView, updateView, type ViewResource, type ViewTableState } from "../../api/resources/views";
import { messageForError } from "../../api/errors";
import { queryKeys } from "../../api/queryKeys";
import { t } from "../../i18n";
import { useNotify } from "../../notify/NotifyProvider";
import { useBridge } from "../pane/bridge";

export interface ViewActionsProps {
  // The type the views are of: the list's own registry key.
  entityType: string;
  organizationId: number;
  // The view the list is on, once loaded; absent on the default list.
  activeView?: ViewResource;
  // The list's state as a saved view stores it.
  currentViewState: () => ViewTableState;
  // Opens a view; without an id, the default list.
  onOpen: (viewId?: number) => void;
  // The list goes back to the view's state (or the default one): the user's changes are given up.
  onReset: () => void;
  // The user's changes on the view just moved into the view itself: nothing is left to give up.
  onUpdated: () => void;
}

/**
 * The header's view button, shown once the list has been changed (the old JSF titlebar's split button):
 * on a view of the user's own, « Mettre à jour » writes the changes into it; otherwise « Enregistrer
 * comme nouvelle vue » keeps them as a new view, named in a small dialog. Either way the other, and
 * « Réinitialiser la vue » (give the changes up), are in the button's menu.
 */
export function ViewActions({
  entityType,
  organizationId,
  activeView,
  currentViewState,
  onOpen,
  onReset,
  onUpdated,
}: ViewActionsProps) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const bridge = useBridge();
  const [saveAsOpen, setSaveAsOpen] = useState(false);
  const [title, setTitle] = useState("");

  const afterChange = (view: ViewResource) => {
    queryClient.setQueryData(queryKeys.view(organizationId, view.id), view);
    void queryClient.invalidateQueries({ queryKey: queryKeys.views(organizationId) });
    // The sidebar's views list (JSF) shows it too.
    bridge.refreshViews?.();
  };
  const fail = (error: unknown) => notify.error(messageForError(error, t("views.failed")));

  const create = useMutation({
    mutationFn: (name: string) =>
      createView({ organizationId, resourceType: entityType, title: name, state: currentViewState() }),
    onSuccess: (view) => {
      afterChange(view);
      setSaveAsOpen(false);
      notify.success(t("views.created"));
      // The changes moved into the new view: the list they come from goes back to its own state.
      onReset();
      onOpen(view.id);
    },
    onError: fail,
  });

  const update = useMutation({
    mutationFn: (view: ViewResource) =>
      updateView(view.id, {
        organizationId,
        resourceType: view.resourceType,
        title: view.title,
        scopeEntityType: view.scopeEntityType ?? undefined,
        state: currentViewState(),
      }),
    onSuccess: (view) => {
      afterChange(view);
      onUpdated();
      notify.success(t("views.updated"));
    },
    onError: fail,
  });

  function openSaveAs() {
    setTitle("");
    setSaveAsOpen(true);
  }

  function submitSaveAs(e: FormEvent) {
    e.preventDefault();
    const name = title.trim();
    if (name) create.mutate(name);
  }

  const saveAs = { label: t("views.saveAs"), icon: "bi bi-bookmark-plus", command: openSaveAs };
  const reset = { label: t("views.reset"), icon: "bi bi-arrow-clockwise", command: onReset };
  const ownView = activeView?.canEdit === true ? activeView : undefined;

  return (
    <>
      {ownView ? (
        <SplitButton
          className="entity-list-panel-views-button"
          menuClassName="entity-list-panel-views-menu"
          outlined
          label={t("views.update")}
          icon="bi bi-file-earmark-spreadsheet"
          size="small"
          disabled={update.isPending}
          onClick={() => update.mutate(ownView)}
          model={[saveAs, reset]}
        />
      ) : (
        <SplitButton
          className="entity-list-panel-views-button"
          menuClassName="entity-list-panel-views-menu"
          outlined
          label={t("views.saveAs")}
          icon="bi bi-file-earmark-spreadsheet"
          size="small"
          onClick={openSaveAs}
          model={[reset]}
        />
      )}

      <Dialog header={t("views.dialogTitle")} visible={saveAsOpen} onHide={() => setSaveAsOpen(false)} style={{ width: "26rem" }} draggable={false}>
        <form onSubmit={submitSaveAs} className="entity-list-panel-views-form">
          <label htmlFor="entity-list-view-title">{t("views.titleLabel")}</label>
          <InputText id="entity-list-view-title" value={title} maxLength={200} autoFocus onChange={(e) => setTitle(e.target.value)} />
          <div className="entity-list-panel-views-form-actions">
            <Button type="button" label={t("views.cancel")} text onClick={() => setSaveAsOpen(false)} />
            <Button type="submit" label={t("views.save")} disabled={!title.trim() || create.isPending} />
          </div>
        </form>
      </Dialog>
    </>
  );
}
