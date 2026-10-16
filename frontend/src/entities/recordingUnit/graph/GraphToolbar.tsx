import { useRef } from "react";
import { Button } from "primereact/button";
import { Checkbox } from "primereact/checkbox";
import { OverlayPanel } from "primereact/overlaypanel";
import { Toolbar } from "primereact/toolbar";
import { t } from "../../../i18n";

export interface GraphOptions {
  // Each unit linked to the center by its own line; off, one arrow per zone.
  showLinks: boolean;
}

// The bar above the graph, the list toolbar's own: its gear opens the graph's display options. It
// will also hold the switch to the list view.
export function GraphToolbar({ options, onChange }: { options: GraphOptions; onChange: (options: GraphOptions) => void }) {
  const overlayRef = useRef<OverlayPanel>(null);
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
            <label className="sia-graph-setting">
              <Checkbox inputId="graph-show-links" checked={options.showLinks} onChange={(e) => onChange({ ...options, showLinks: !!e.checked })} />
              <span>{t("recordingUnit.graph.showLinks")}</span>
            </label>
          </OverlayPanel>
        </>
      )}
    />
  );
}
