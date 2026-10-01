import { createContext, useContext, type ReactNode } from "react";

/**
 * The app's global read/write mode — FlowBean.isWriteMode, driven by the one switch in
 * pages/shared/topbar.xhtml.
 *
 * <p>In JSF every editable affordance is gated on it: the panel header's pencil
 * (headerEditControls.xhtml, together with the user's own right on the entity —
 * {@code canUserEditUnit()}, bug #448), a table cell's edit control (entityDataTable.xhtml's
 * "writeMode" rendering rule), the toolbar's create buttons, and the form fields themselves
 * (FlowBean.getInPlaceFieldMode() returns "input" or "output" off this flag alone). React honours
 * the same gate rather than offering controls the server would refuse.</p>
 *
 * <p>A context rather than a prop threaded through App → PanelContent → panel → tab: every panel
 * kind needs it, at several depths, and none of the layers in between has any other reason to know
 * about it. It never changes within a mount's lifetime — the topbar switch's p:ajax does
 * update="flow", which replaces the mount container, so a toggle remounts React with the new
 * value (reactPanelBootstrap.js).</p>
 */
const WriteModeContext = createContext(false);

export function WriteModeProvider({ value, children }: { value: boolean; children: ReactNode }) {
  return <WriteModeContext.Provider value={value}>{children}</WriteModeContext.Provider>;
}

export function useWriteMode(): boolean {
  return useContext(WriteModeContext);
}

/**
 * Whether the user may actually edit this entity right now: the global mode AND their own right on
 * it, exactly the two-part gate headerEditControls.xhtml applies.
 *
 * <p>`_permissions` comes straight off the API response and is never recomputed client-side; an
 * entity that carries none is treated as editable, because the only entity type that omits it is
 * one whose endpoint doesn't model permissions at all — the PATCH remains the real enforcement
 * either way, this is only about not offering a control that would 403.</p>
 */
export function useCanEdit(entity: { _permissions?: { canEdit?: boolean } } | null | undefined): boolean {
  const writeMode = useWriteMode();
  // No entity is not "permission unknown, assume yes" — there is nothing to edit yet (the detail
  // panel still loading, say), so the answer is no until one arrives.
  if (entity == null) return false;
  return writeMode && entity._permissions?.canEdit !== false;
}
