import {useMemo, useRef} from "react";
import "leaflet/dist/leaflet.css";
import {Skeleton} from "primereact/skeleton";
import {type MapFeature, type ResultSetParams, resultSetOf} from "../../entities/shared/listApi";
import type {EntityPreview, EntityTypeConfig} from "../../entities/shared/types";
import {LIST_LOOK, SELECTED_LOOK} from "../../map/featureLook";
import {fitLayer, revealLayer} from "../../map/leafletMap";
import {useMapFocusTarget} from "../../map/mapFocus";
import {useFeatureLayer} from "../../map/useFeatureLayer";
import {useLeafletMap} from "../../map/useLeafletMap";
import {isOpenInOverview} from "./overviewSelection";
import {MapNotices} from "./map/MapNotices";
import {summarizeMap} from "./map/mapSummary";
import {useEntityMapData} from "./map/useEntityMapData";
import {useSelectedFeature} from "./map/useSelectedFeature";

export interface EntityMapViewProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  // The list's result set (search, filters, scope) — the map shows the same rows.
  params: ResultSetParams;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  // The fiche the overview pane shows: highlighted and kept in view, even when the list's filter leaves it out.
  selectedId?: string | number;
  // The geometry column the rows are placed by (the settings' « Géométrie affichée »); their own geometry when absent.
  geometry?: string;
}

const NO_FEATURES: readonly MapFeature[] = [];

/**
 * The list on a map, for an entity whose config is `mappable`. Leaflet draws the features the
 * server returned (already in WGS84) by the chosen geometry column; a click opens the entity like the table's identifier chip,
 * and the fiche open in the overview stays highlighted and in view (fetched on its own only when
 * the result set left it out). Units the server could not
 * place are counted in a banner above the map, not guessed at.
 */
export function EntityMapView({ entityType, config, params, onNavigate, onOpenOverview, selectedId, geometry }: Readonly<EntityMapViewProps>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const map = useLeafletMap(containerRef);
  const { data, isLoading, error } = useEntityMapData(entityType, config.collectionPath, params, true, geometry);
  const features = data?.features ?? NO_FEATURES;

  // Framed once per result set and geometry column: a refetch of the same rows (after an edit) leaves the user's view alone.
  const resultSetKey = JSON.stringify([resultSetOf(params), geometry ?? null]);
  const framedFor = useRef<string | null>(null);
  useFeatureLayer(map, features, LIST_LOOK, {
    onOpen: (feature) => {
      if (onOpenOverview) onOpenOverview(entityType, feature.id, { label: feature.label, validationStatus: feature.validationStatus });
      else onNavigate?.(entityType, feature.id);
    },
    onDrawn: (m, layer) => {
      if (framedFor.current !== resultSetKey && fitLayer(m, layer)) framedFor.current = resultSetKey;
    },
  });

  const selected = useSelectedFeature(entityType, data?.features, selectedId, geometry);
  const selection = useMemo(() => (selected ? [selected] : NO_FEATURES), [selected]);
  const selectionLayer = useFeatureLayer(map, selection, SELECTED_LOOK, { onDrawn: revealLayer });
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
