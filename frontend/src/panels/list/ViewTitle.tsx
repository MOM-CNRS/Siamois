import { useState, type KeyboardEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { InputText } from "primereact/inputtext";
import { updateView, type ViewResource } from "../../api/resources/views";
import { messageForError } from "../../api/errors";
import { queryKeys } from "../../api/queryKeys";
import { t } from "../../i18n";
import { useNotify } from "../../notify/NotifyProvider";
import { useBridge } from "../pane/bridge";

export interface ViewTitleProps {
  view: ViewResource;
  organizationId: number;
}

/**
 * The list's title on a saved view: the view's name, in place of the type's. The owner renames it by clicking
 * it (Enter or leaving the field saves, Escape gives up); anybody else reads it.
 */
export function ViewTitle({ view, organizationId }: ViewTitleProps) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const bridge = useBridge();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(view.title);

  const rename = useMutation({
    mutationFn: (title: string) =>
      updateView(view.id, {
        organizationId,
        resourceType: view.resourceType,
        title,
        scopeEntityType: view.scopeEntityType ?? undefined,
        state: view.state,
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.view(organizationId, saved.id), saved);
      void queryClient.invalidateQueries({ queryKey: queryKeys.views(organizationId) });
      bridge.refreshViews?.();
      notify.success(t("views.renamed"));
    },
    onError: (error) => notify.error(messageForError(error, t("views.failed"))),
  });

  function open() {
    setDraft(view.title);
    setEditing(true);
  }

  function commit() {
    setEditing(false);
    const title = draft.trim();
    if (title && title !== view.title) rename.mutate(title);
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") commit();
    if (e.key === "Escape") setEditing(false);
  }

  if (!view.canEdit) return <span className="sia-list-title-text">{view.title}</span>;

  return editing ? (
    <InputText
      className="entity-list-panel-view-title-input"
      value={draft}
      maxLength={200}
      autoFocus
      aria-label={t("views.titleLabel")}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={onKeyDown}
    />
  ) : (
    <button type="button" className="sia-list-title-text entity-list-panel-view-title" title={t("views.rename")} onClick={open}>
      {view.title}
      <i className="bi bi-pencil entity-list-panel-view-title-icon" aria-hidden="true" />
    </button>
  );
}
