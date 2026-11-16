import {useMemo, useState} from "react";
import {Button} from "primereact/button";
import {Message} from "primereact/message";
import {Skeleton} from "primereact/skeleton";
import {t} from "../i18n";
import {FeatureMap} from "../map/FeatureMap";
import {geometryKind} from "../map/geometryKind";
import {focusOnMap} from "../map/mapFocus";
import type {FocusSource} from "../map/drawingFocus";
import {useEntityFeature} from "../map/useEntityFeature";
import {GeometryEditor} from "./GeometryEditor";

export interface GeometryMapFieldProps {
  // The geometry's WKT, null while the entity has none.
  wkt: string | null;
  entityType: string;
  entityId: string | number;
  // The places the fiche names and its project: where the editor frames a map with no shape yet.
  focusSource: FocusSource;
  // The user may draw the geometry (write mode, edit right, an entity type that saves one).
  editable: boolean;
  onSaved: () => void;
}

type Panel = "none" | "view" | "edit";

/**
 * The geometry field of an entity that can be placed on a map. Filled: a chip naming the geometry's
 * type; a click zooms onto the shape in the list's map when one shows it beside the fiche (map view),
 * and otherwise opens a closable map of the shape below the chip. Editable: a button opens a map to
 * draw the shape (polygons or a point), replacing the current one or removing it.
 */
export function GeometryMapField({ wkt, entityType, entityId, focusSource, editable, onSaved }: Readonly<GeometryMapFieldProps>) {
  const [panel, setPanel] = useState<Panel>("none");
  const toggle = (next: Panel) => setPanel((current) => (current === next ? "none" : next));

  return (
    <div className="field-geometry-map">
      <div className="field-geometry-map-controls">
        {wkt && <GeometryChip wkt={wkt} open={panel === "view"} onClick={() => focusOnMap(entityType, entityId) || toggle("view")} />}
        {editable && (
          <Button
            type="button"
            icon={wkt ? "pi pi-pencil" : "bi bi-pentagon"}
            label={wkt ? undefined : t("map.drawShape")}
            text
            rounded={wkt != null}
            size="small"
            className="field-geometry-edit"
            aria-label={wkt ? t("map.editShape") : t("map.drawShape")}
            title={wkt ? t("map.editShape") : t("map.drawShape")}
            aria-expanded={panel === "edit"}
            onClick={(e) => {
              e.stopPropagation();
              toggle("edit");
            }}
          />
        )}
      </div>
      {panel === "view" && wkt && <GeometryMapPanel wkt={wkt} entityType={entityType} entityId={entityId} onClose={() => setPanel("none")} />}
      {panel === "edit" && (
        <GeometryEditor
          entityType={entityType}
          entityId={entityId}
          focusSource={focusSource}
          wkt={wkt}
          onClose={() => setPanel("none")}
          onSaved={onSaved}
        />
      )}
    </div>
  );
}

// The chip naming the geometry's type.
function GeometryChip({ wkt, open, onClick }: Readonly<{ wkt: string; open: boolean; onClick: () => void }>) {
  const kind = geometryKind(wkt);
  return (
    <button
      type="button"
      className="ref-chip ref-chip-link field-geometry-chip"
      title={t("map.showShape")}
      aria-expanded={open}
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
    >
      <i className={`${kind.icon} ref-chip-icon`} aria-hidden="true" />
      <span className="ref-chip-label">{t(kind.label)}</span>
    </button>
  );
}

// The map under the chip: the shape PostGIS placed in WGS84, or why it can't be placed (with its WKT).
function GeometryMapPanel({ wkt, entityType, entityId, onClose }: Readonly<{ wkt: string; entityType: string; entityId: string | number; onClose: () => void }>) {
  const { feature, isLoading } = useEntityFeature(entityType, entityId);
  const features = useMemo(() => (feature ? [feature] : []), [feature]);

  function body() {
    if (isLoading) return <Skeleton className="field-geometry-map-canvas" height="auto" borderRadius="4px" />;
    if (feature) return <FeatureMap features={features} className="field-geometry-map-canvas" />;
    return (
      <>
        <Message severity="info" className="field-geometry-map-notice" text={t("map.notPlaceable")} />
        <code className="field-geometry">{wkt}</code>
      </>
    );
  }

  return (
    <div className="field-geometry-map-panel">
      <div className="field-geometry-map-header">
        <Button
          type="button"
          icon="pi pi-times"
          text
          rounded
          size="small"
          className="field-geometry-map-close"
          aria-label={t("map.closeShape")}
          title={t("map.closeShape")}
          onClick={onClose}
        />
      </div>
      {body()}
    </div>
  );
}
