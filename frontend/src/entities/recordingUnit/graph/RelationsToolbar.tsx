import { useRef, type ReactNode } from "react";
import { Button } from "primereact/button";
import { Checkbox } from "primereact/checkbox";
import { OverlayPanel } from "primereact/overlaypanel";
import { RadioButton } from "primereact/radiobutton";
import { Toolbar } from "primereact/toolbar";
import { t } from "../../../i18n";

export type RelationsMode = "graph" | "list";

export interface RelationsToolbarProps {
  mode: RelationsMode;
  onModeChange: (mode: RelationsMode) => void;
  // Graph only: each unit linked to the center by its own line; off, one arrow per zone.
  showLinks: boolean;
  onShowLinksChange: (showLinks: boolean) => void;
  // The list's own controls (search, filters), after the gear.
  start?: ReactNode;
  // What sits at the right end: the count, the add button.
  end?: ReactNode;
}

// The bar above the relations, the list toolbar's own: its gear opens the display settings — the
// graph or the list, and the graph's own options.
export function RelationsToolbar({ mode, onModeChange, showLinks, onShowLinksChange, start, end }: RelationsToolbarProps) {
  const overlayRef = useRef<OverlayPanel>(null);
  const modes: Array<{ value: RelationsMode; label: string; icon: string }> = [
    { value: "graph", label: t("recordingUnit.relations.mode.graph"), icon: "bi bi-diagram-3" },
    { value: "list", label: t("recordingUnit.relations.mode.list"), icon: "bi bi-list-ul" },
  ];
  return (
    <Toolbar
      className="entity-list-panel-toolbar"
      start={(
        <>
          <Button
            icon="bi bi-gear"
            text
            rounded
            size="large"
            aria-label={t("recordingUnit.graph.settings")}
            aria-haspopup
            tooltip={t("recordingUnit.graph.settings")}
            className="entity-list-panel-gear-button"
            onClick={(e) => overlayRef.current?.toggle(e)}
          />
          <OverlayPanel ref={overlayRef} className="entity-list-panel-settings-overlay">
            <div className="sia-graph-setting-group" role="radiogroup" aria-label={t("recordingUnit.relations.mode.title")}>
              <strong className="sia-graph-setting-title">{t("recordingUnit.relations.mode.title")}</strong>
              {modes.map((option) => (
                <label key={option.value} className="sia-graph-setting">
                  <RadioButton inputId={`relations-mode-${option.value}`} name="relations-mode" checked={mode === option.value} onChange={() => onModeChange(option.value)} />
                  <span><i className={option.icon} aria-hidden="true" /> {option.label}</span>
                </label>
              ))}
            </div>
            {mode === "graph" && (
              <label className="sia-graph-setting">
                <Checkbox inputId="graph-show-links" checked={showLinks} onChange={(e) => onShowLinksChange(!!e.checked)} />
                <span>{t("recordingUnit.graph.showLinks")}</span>
              </label>
            )}
          </OverlayPanel>
          {start}
        </>
      )}
      end={end}
    />
  );
}
