import {useEffect, useMemo, useRef} from "react";
import "leaflet/dist/leaflet.css";
import {Skeleton} from "primereact/skeleton";
import {type MapFeature, type MapParams, resultSetOf} from "../../entities/shared/listApi";
import type L from "leaflet";
import type {EntityPreview, EntityTypeConfig} from "../../entities/shared/types";
import {LIST_LOOK, SELECTED_LOOK} from "../../map/featureLook";
import {centerLayer, fitLayer, focusLayer, revealLayer} from "../../map/leafletMap";
import {useMapFocusTarget} from "../../map/mapFocus";
import {useFeatureLayer} from "../../map/useFeatureLayer";
import {useLeafletMap} from "../../map/useLeafletMap";
import {rememberListContext} from "./listContext";
import {isOpenInOverview} from "./overviewSelection";
import {MapNotices} from "./map/MapNotices";
import {summarizeMap} from "./map/mapSummary";
import {mapWalkContext} from "./map/mapWalk";
import {useEntityMapData} from "./map/useEntityMapData";
import {useSelectedFeature} from "./map/useSelectedFeature";

export interface EntityMapViewProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  // The list's result set (search, filters, scope) and order — the map shows the same rows, in that order.
  params: MapParams;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  // The fiche the overview pane shows: highlighted and kept in view, even when the list's filter leaves it out.
  selectedId?: string | number;
  // The geometry column the rows are placed by (the settings' « Géométrie affichée »); their own geometry when absent.
  geometry?: string;
}

const NO_FEATURES: readonly MapFeature[] = [];

// What the overview header shows of a feature while its fiche loads.
const previewOf = (feature: MapFeature): EntityPreview => ({ label: feature.label, validationStatus: feature.validationStatus });

/**
 * The list on a map, for an entity whose config is `mappable`. Leaflet draws the features the
 * server returned (already in WGS84) by the chosen geometry column; a click opens the entity like the table's identifier chip,
 * and the fiche open in the overview stays highlighted and in view (fetched on its own only when
 * the result set left it out). Units the server could not
 * place are counted in a banner above the map, not guessed at.
 *
 * Beside an overview, the map opens its first feature there on its first display when no fiche of
 * the type is open yet, and zooms onto the open fiche; each fiche opened afterwards (the overview's
 * previous/next arrows, a click) is brought to the middle of the map. A fiche opened on the map walks
 * the map's features with those arrows, in their order, like a row opened in the table walks the table.
 */
export function EntityMapView({ entityType, config, params, onNavigate, onOpenOverview, selectedId, geometry }: Readonly<EntityMapViewProps>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const map = useLeafletMap(containerRef);
  const { data, isLoading, error } = useEntityMapData(entityType, config.collectionPath, params, true, geometry);
  const features = data?.features ?? NO_FEATURES;

  // Framed once per result set and geometry column: a refetch of the same rows (after an edit) leaves the user's view alone.
  const resultSetKey = JSON.stringify([resultSetOf(params), geometry ?? null]);
  const framedFor = useRef<string | null>(null);
  // The fiche's previous/next arrows walk the map's features from this one's position (panels/list/listContext.ts).
  const walkFrom = (id: string | number) => {
    const context = mapWalkContext(params, geometry, features, id);
    if (context) rememberListContext(entityType, id, context);
  };
  useFeatureLayer(map, features, LIST_LOOK, {
    onOpen: (feature) => {
      walkFrom(feature.id);
      if (onOpenOverview) onOpenOverview(entityType, feature.id, previewOf(feature));
      else onNavigate?.(entityType, feature.id);
    },
    onDrawn: (m, layer) => {
      if (framedFor.current !== resultSetKey && fitLayer(m, layer)) framedFor.current = resultSetKey;
    },
  });

  // The first display beside an overview (not in focus mode) opens the first feature, unless a fiche is
  // already open: that one then walks the map's features too, if the map shows it.
  const firstDisplayed = useRef(false);
  const walkFromRef = useRef(walkFrom);
  walkFromRef.current = walkFrom;
  const firstFeature = features[0];
  useEffect(() => {
    if (firstDisplayed.current || !onOpenOverview || !firstFeature) return;
    firstDisplayed.current = true;
    walkFromRef.current(selectedId ?? firstFeature.id);
    if (selectedId == null) onOpenOverview(entityType, firstFeature.id, previewOf(firstFeature));
  }, [entityType, firstFeature, onOpenOverview, selectedId]);

  const selected = useSelectedFeature(entityType, data?.features, selectedId, geometry);
  const selection = useMemo(() => (selected ? [selected] : NO_FEATURES), [selected]);
  // The fiche last drawn as the selection: undefined until one is, so the first gets the zoom.
  const shownSelection = useRef<string | undefined>(undefined);
  const showSelection = (m: L.Map, layer: L.GeoJSON) => {
    if (!selected) return;
    const previous = shownSelection.current;
    shownSelection.current = selected.id;
    if (previous === undefined) focusLayer(m, layer);
    else if (previous === selected.id) revealLayer(m, layer); // the same fiche redrawn (a refetch): kept in view only
    else centerLayer(m, layer);
  };
  const selectionLayer = useFeatureLayer(map, selection, SELECTED_LOOK, { onDrawn: showSelection });
  // The fiche's geometry chip, in the overview beside this map, zooms here onto its shape.
  useMapFocusTarget((type, id) => {
    const layer = selectionLayer.current;
    if (!map || !layer || type !== entityType || !isOpenInOverview(id, selected?.id)) return false;
    return fitLayer(map, layer, true);
  });

  return (
    <div className="entity-map-view">
      {error && <div className="entity-list-panel-error">{(error as Error).message}</div>}
      {data && <MapNotices summary={summarizeMap(data)} />}
      <div className="entity-map-frame">
        <div ref={containerRef} className="entity-map-canvas" />
        {isLoading && <Skeleton className="entity-map-loading" height="100%" borderRadius="0" />}
      </div>
    </div>
  );
}
