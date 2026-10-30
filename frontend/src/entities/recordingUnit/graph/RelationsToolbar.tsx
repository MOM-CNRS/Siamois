import {type ReactNode, useRef} from "react";
import {Button} from "primereact/button";
import {InputSwitch} from "primereact/inputswitch";
import {OverlayPanel} from "primereact/overlaypanel";
import {Toolbar} from "primereact/toolbar";
import {t} from "../../../i18n";

export type RelationsMode = "graph" | "list";

export interface RelationsToolbarProps {
  mode: RelationsMode;
  onModeChange: (mode: RelationsMode) => void;
  // Graph only: each unit linked to the center by its own line, naming the relation; off, one arrow per zone.
  detailed: boolean;
  onDetailedChange: (detailed: boolean) => void;
  // The list's own controls (search, filters), after the gear.
  start?: ReactNode;
  // What sits at the right end: the count, the add button.
  end?: ReactNode;
}

// The bar above the relations, the list toolbar's own: its gear opens the display settings in the same overlay
// as the lists' — the layout as tiles (the graph or the list), then the graph's own option.
export function RelationsToolbar({ mode, onModeChange, detailed, onDetailedChange, start, end }: RelationsToolbarProps) {
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
          <OverlayPanel ref={overlayRef} className="entity-list-panel-settings-overlay entity-list-panel-gear-panel">
            <div className="entity-list-panel-view-mode" role="radiogroup" aria-label={t("list.menuView")}>
              <span className="entity-list-panel-view-mode-title">{t("list.menuView")}</span>
              <div className="entity-list-panel-view-mode-grid">
                {modes.map((option) => {
                  const active = option.value === mode;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      role="radio"
                      aria-checked={active}
                      className={`entity-list-panel-view-tile${active ? " is-active" : ""}`}
                      onClick={() => {
                        if (active) return;
                        overlayRef.current?.hide();
                        onModeChange(option.value);
                      }}
                    >
                      <i className={option.icon} aria-hidden="true" />
                      <span>{option.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>
            {mode === "graph" && (
              <label className="sia-graph-setting">
                <InputSwitch inputId="graph-detailed" checked={detailed} onChange={(e) => onDetailedChange(!!e.value)} />
                <span>{t("recordingUnit.graph.detailed")}</span>
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
