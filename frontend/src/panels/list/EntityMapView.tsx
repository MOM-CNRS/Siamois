import {useRef} from "react";
import "leaflet/dist/leaflet.css";
import {Skeleton} from "primereact/skeleton";
import {type MapFeature, type ResultSetParams, resultSetOf} from "../../entities/shared/listApi";
import type {EntityPreview, EntityTypeConfig} from "../../entities/shared/types";
import {LIST_LOOK} from "../../map/featureLook";
import {fitLayer} from "../../map/leafletMap";
import {useFeatureLayer} from "../../map/useFeatureLayer";
import {useLeafletMap} from "../../map/useLeafletMap";
import {MapNotices} from "./map/MapNotices";
import {summarizeMap} from "./map/mapSummary";
import {useEntityMapData} from "./map/useEntityMapData";

export interface EntityMapViewProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  // The list's result set (search, filters, scope) — the map shows the same rows.
  params: ResultSetParams;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
}

const NO_FEATURES: readonly MapFeature[] = [];

/**
 * The list on a map, for an entity whose config is `mappable`. Leaflet draws the features the
 * server returned (already in WGS84); a click opens the entity like the table's identifier chip.
 * Units the server could not place are counted in a banner above the map, not guessed at.
 */
export function EntityMapView({ entityType, config, params, onNavigate, onOpenOverview }: EntityMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const map = useLeafletMap(containerRef);
  const { data, isLoading, error } = useEntityMapData(entityType, config.collectionPath, params);
  const features = data?.features ?? NO_FEATURES;

  // Framed once per result set: a refetch of the same rows (after an edit) leaves the user's view alone.
  const resultSetKey = JSON.stringify(resultSetOf(params));
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
