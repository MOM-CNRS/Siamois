import { useRef } from "react";
import { Tooltip } from "primereact/tooltip";
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
 * every entity's fiche without per-entity work.
 *
 * Targets the Tooltip by ref rather than a `#<id>` CSS selector (JSF's own `p:tooltip for=`
 * equivalent): {@code useId()} would be the natural way to keep two simultaneously-mounted
 * instances (the main pane's fiche and the overview pane's fiche) from colliding, but its ids
 * contain `:`, which is not a valid unescaped CSS identifier character and breaks PrimeReact's
 * own `querySelectorAll(target)` lookup. A ref sidesteps CSS entirely and needs no escaping.
 */
export function SiblingNav({ siblings, onNavigate, disabled }: SiblingNavProps) {
  const previousRef = useRef<HTMLAnchorElement>(null);
  const nextRef = useRef<HTMLAnchorElement>(null);

  const previous = siblings?.previous;
  const next = siblings?.next;
  const previousEnabled = previous != null && !disabled;
  const nextEnabled = next != null && !disabled;

  return (
    <div style={{ display: "flex" }}>
      <a
        ref={previousRef}
        role="button"
        aria-disabled={!previousEnabled}
        className="sideview-topbar-button"
        style={{ opacity: previousEnabled ? 1 : 0.35, cursor: previousEnabled ? "pointer" : "default" }}
        onClick={() => previousEnabled && onNavigate(previous.id)}
      >
        <i className="ui-icon bi bi-caret-up" style={{ color: "var(--main-color)" }} />
      </a>
      {previous && <Tooltip target={previousRef} content={`Fiche précédente — ${previous.label}`} position="bottom" />}
      <a
        ref={nextRef}
        role="button"
        aria-disabled={!nextEnabled}
        className="sideview-topbar-button"
        style={{ opacity: nextEnabled ? 1 : 0.35, cursor: nextEnabled ? "pointer" : "default" }}
        onClick={() => nextEnabled && onNavigate(next.id)}
      >
        <i className="ui-icon bi bi-caret-down" style={{ color: "var(--main-color)" }} />
      </a>
      {next && <Tooltip target={nextRef} content={`Fiche suivante — ${next.label}`} position="bottom" />}
    </div>
  );
}
