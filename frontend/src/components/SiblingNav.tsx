import { Button } from "primereact/button";
import type { EntitySiblings } from "../entities/types";

export interface SiblingNavProps {
  // undefined while the siblings query hasn't resolved yet — both arrows render disabled rather
  // than popping in after the fact, matching how the rest of the panel header waits on its own
  // query before rendering.
  siblings: EntitySiblings | undefined;
  onNavigate: (id: string | number) => void;
  // True while the overview pane's own setOverview bridge call is in flight (App.tsx's
  // overviewBusy) — same reason the overview toolbar's own actions are withheld then.
  disabled?: boolean;
}

/**
 * "Fiche précédente/suivante" — the two caret buttons every JSF single-entity panel header
 * renders (actionUnitPanelHeader.xhtml, recordingUnitPanelHeader.xhtml, ...), all calling the
 * same `panelModel.goToPrevious()/goToNext()`. Rendered generically by EntityDetailPanel (not by
 * any one entity's own header component) for the same reason it's generic in JSF: portable to
 * every entity's fiche without per-entity work. Same PrimeReact buttons as the rest of the
 * panel toolbar, whose navigation group they sit in.
 */
export function SiblingNav({ siblings, onNavigate, disabled }: SiblingNavProps) {
  const previous = siblings?.previous;
  const next = siblings?.next;

  return (
    <>
      <Button
        icon="bi bi-caret-up"
        className="sideview-topbar-button"
        text
        rounded
        aria-label="Fiche précédente"
        tooltip={previous ? `Fiche précédente — ${previous.label}` : undefined}
        tooltipOptions={{ position: "bottom" }}
        disabled={previous == null || disabled}
        onClick={() => previous && onNavigate(previous.id)}
      />
      <Button
        icon="bi bi-caret-down"
        className="sideview-topbar-button"
        text
        rounded
        aria-label="Fiche suivante"
        tooltip={next ? `Fiche suivante — ${next.label}` : undefined}
        tooltipOptions={{ position: "bottom" }}
        disabled={next == null || disabled}
        onClick={() => next && onNavigate(next.id)}
      />
    </>
  );
}
