import {useMemo, useRef} from "react";
import "leaflet/dist/leaflet.css";
import {useMutation, useQueryClient} from "@tanstack/react-query";
import {Button} from "primereact/button";
import {SelectButton} from "primereact/selectbutton";
import {queryKeys} from "../api/queryKeys";
import {messageForError} from "../api/errors";
import {getEntityType} from "../entities/registry";
import type {MapFeature} from "../entities/shared/listApi";
import {t} from "../i18n";
import type {FocusSource} from "../map/drawingFocus";
import {CONTEXT_LOOK, SELECTED_LOOK} from "../map/featureLook";
import {geometryKind} from "../map/geometryKind";
import {fitLayer} from "../map/leafletMap";
import {canCloseRing, canSave, type DraftMode, draftToGeometry, isEmpty} from "../map/shapeDraft";
import {useDrawingFocus} from "../map/useDrawingFocus";
import {useEntityFeature} from "../map/useEntityFeature";
import {useFeatureLayer} from "../map/useFeatureLayer";
import {useLeafletMap} from "../map/useLeafletMap";
import {useShapeDraft} from "../map/useShapeDraft";
import {useNotify} from "../notify/NotifyProvider";
import {useEntityMapData} from "../panels/list/map/useEntityMapData";

export interface GeometryEditorProps {
  entityType: string;
  entityId: string | number;
  // The places the fiche names and its project: where to frame the map when it has no shape yet.
  focusSource: FocusSource;
  // The current geometry's WKT, null when it has none.
  wkt: string | null;
  onClose: () => void;
  onSaved: () => void;
}

const NO_FEATURES: readonly MapFeature[] = [];

/**
 * Draws an entity's geometry — one or several polygons, or a single point — one click at a time. The
 * map is framed on the current shape (shown as the reference it replaces); without one, on the INSEE
 * commune of the fiche's place, else on the widest place of its project, else left where it is. The
 * project's other units are drawn faintly around. Saved through the entity config's
 * `api.patchGeometry`, in WGS84; the current shape can also be removed.
 */
export function GeometryEditor({ entityType, entityId, focusSource, wkt, onClose, onSaved }: Readonly<GeometryEditorProps>) {
  const config = getEntityType(entityType);
  const hasGeometry = wkt != null;
  const containerRef = useRef<HTMLDivElement>(null);
  const map = useLeafletMap(containerRef);
  // Redrawing a point starts in point mode, anything else (or nothing yet) in polygon mode.
  const drawing = useShapeDraft(map, wkt && geometryKind(wkt).shape === "point" ? "point" : "polygon");
  const { draft } = drawing;
  const queryClient = useQueryClient();
  const notify = useNotify();

  const { feature } = useEntityFeature(entityType, entityId, hasGeometry);
  const reference = useMemo(() => (feature ? [feature] : NO_FEATURES), [feature]);
  const focus = useDrawingFocus(focusSource, !hasGeometry);
  const focusArea = useMemo(() => (focus ? [{ id: "focus", label: "", geometry: focus }] : NO_FEATURES), [focus]);
  const { projectId } = focusSource;
  const projectMap = useEntityMapData(
    entityType,
    config?.collectionPath ?? "",
    { scope: projectId ? { entityType: "project", id: projectId } : undefined },
    projectId != null && config != null,
  );
  const neighbours = useMemo(
    () => (projectMap.data?.features ?? NO_FEATURES).filter((f) => String(f.id) !== String(entityId)),
    [projectMap.data, entityId],
  );

  // Framed once: on the shape itself, else on the place the focus found; the neighbours never move the map.
  const framed = useRef(false);
  const frameOnce = (m: Parameters<typeof fitLayer>[0], layer: Parameters<typeof fitLayer>[1]) => {
    if (!framed.current && fitLayer(m, layer)) framed.current = true;
  };
  useFeatureLayer(map, focusArea, CONTEXT_LOOK, { onDrawn: frameOnce });
  useFeatureLayer(map, neighbours, CONTEXT_LOOK, {});
  useFeatureLayer(map, reference, SELECTED_LOOK, { onDrawn: frameOnce });

  const save = useMutation({
    mutationFn: (geometry: GeoJSON.Geometry | null) => config!.api.patchGeometry!(entityId, geometry),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityMapFeature(entityType, entityId) });
      onSaved();
      onClose();
    },
    onError: (err) => notify.error(messageForError(err, t("map.saveFailed"))),
  });
  const modes: { label: string; value: DraftMode; icon: string }[] = [
    { label: t("map.modePolygons"), value: "polygon", icon: "bi bi-pentagon" },
    { label: t("map.modePoint"), value: "point", icon: "bi bi-geo-alt" },
  ];

  return (
    <div className="field-geometry-map-panel">
      <div className="field-geometry-map-header">
        <SelectButton
          className="field-geometry-editor-mode"
          value={draft.mode}
          options={modes}
          allowEmpty={false}
          itemTemplate={(option: (typeof modes)[number]) => (
            <span>
              <i className={`${option.icon} field-geometry-editor-mode-icon`} aria-hidden="true" />
              {option.label}
            </span>
          )}
          onChange={(e) => e.value && drawing.setMode(e.value as DraftMode)}
        />
        <span className="field-geometry-editor-hint">{t(draft.mode === "point" ? "map.drawPointHint" : "map.drawHint")}</span>
        <Button type="button" icon="pi pi-times" text rounded size="small" aria-label={t("common.cancel")} title={t("common.cancel")} onClick={onClose} />
      </div>
      <div ref={containerRef} className="field-geometry-map-canvas" />
      <div className="field-geometry-editor-actions">
        {draft.mode === "polygon" && (
          <Button type="button" icon="pi pi-check-square" label={t("map.closePolygon")} text size="small" disabled={!canCloseRing(draft)} onClick={drawing.closeRing} />
        )}
        <Button type="button" icon="pi pi-undo" label={t("map.undoVertex")} text size="small" disabled={isEmpty(draft)} onClick={drawing.undo} />
        <Button type="button" icon="pi pi-eraser" label={t("map.clearDraft")} text size="small" disabled={isEmpty(draft)} onClick={drawing.clear} />
        <span className="field-geometry-editor-spacer" />
        {hasGeometry && (
          <Button type="button" icon="pi pi-trash" label={t("map.deleteShape")} severity="danger" text size="small" disabled={save.isPending} onClick={() => save.mutate(null)} />
        )}
        <Button
          type="button"
          icon="pi pi-check"
          label={t("map.saveShape")}
          size="small"
          disabled={!canSave(draft) || save.isPending}
          loading={save.isPending}
          onClick={() => save.mutate(draftToGeometry(draft))}
        />
      </div>
    </div>
  );
}
