import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { Message } from "primereact/message";
import { Skeleton } from "primereact/skeleton";
import { fetchMap, type MapData, type MapFeature } from "../../entities/listApi";
import type { EntityPreview, EntityTypeConfig, ListParams } from "../../entities/types";
import { t } from "../../i18n";

export interface EntityMapViewProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  // The list's result set (search, sort, filters, scope) — the map shows the same rows.
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
}

const OSM = { url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png", attribution: "© OpenStreetMap" };
const SATELLITE = {
  url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
  attribution: "Tiles © Esri",
};

// Tiles stop at zoom 19 (OSM) — past it they are enlarged rather than missing, so a small
// excavation unit can still be looked at closely.
const MAX_ZOOM = 22;

// One colour per type label, stable across renders: a hue from the label's hash.
export function typeColor(label: string | null | undefined): string {
  if (!label) return "#6c757d";
  let hash = 0;
  for (const ch of label) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return `hsl(${hash % 360}, 65%, 45%)`;
}

/**
 * The list on a map, for an entity whose config is `mappable`. Leaflet draws the features the
 * server returned (already in WGS84); a click opens the entity like the table's identifier chip.
 * Units the server could not place are counted in a banner above the map, not guessed at.
 */
export function EntityMapView({ entityType, config, params, onNavigate, onOpenOverview }: EntityMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const layerRef = useRef<L.GeoJSON | null>(null);
  // Read at click time, so the map is not rebuilt when the parent re-renders with new callbacks.
  const openRef = useRef<(f: MapFeature) => void>(() => {});
  openRef.current = (feature) => {
    if (onOpenOverview) onOpenOverview(entityType, feature.id, { label: feature.fullIdentifier, validated: feature.validated });
    else onNavigate?.(entityType, feature.id);
  };

  // Under the list's own prefix, so anything that refreshes the list refreshes its map too.
  const { data, isLoading, error } = useQuery<MapData>({
    queryKey: ["entity-list", entityType, "map", params],
    queryFn: () => fetchMap(config.collectionPath, params),
  });

  useEffect(() => {
    if (!containerRef.current) return;
    const map = L.map(containerRef.current, { zoomControl: true, maxZoom: MAX_ZOOM, zoomSnap: 0.5, zoomDelta: 0.5, wheelPxPerZoomLevel: 90 }).setView([46.6, 2.4], 5);
    const osm = L.tileLayer(OSM.url, { attribution: OSM.attribution, maxNativeZoom: 19, maxZoom: MAX_ZOOM }).addTo(map);
    const satellite = L.tileLayer(SATELLITE.url, { attribution: SATELLITE.attribution, maxNativeZoom: 19, maxZoom: MAX_ZOOM });
    L.control.layers({ [t("list.mapOsm")]: osm, [t("list.mapSatellite")]: satellite }).addTo(map);
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      layerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !data) return;
    layerRef.current?.remove();
    const layer = L.geoJSON(undefined, {
      style: (feature) => {
        const color = typeColor((feature?.properties as MapFeature | undefined)?.type?.resolvedLabel);
        return { color, weight: 2, fillColor: color, fillOpacity: 0.35 };
      },
      // Markers would need Leaflet's own icon images, which a bundler breaks: points are circles.
      pointToLayer: (feature, latlng) => {
        const color = typeColor((feature.properties as MapFeature | undefined)?.type?.resolvedLabel);
        return L.circleMarker(latlng, { radius: 6, color, fillColor: color, fillOpacity: 0.6 });
      },
      onEachFeature: (feature, l) => {
        const props = feature.properties as MapFeature;
        l.bindTooltip(props.fullIdentifier, { sticky: true });
        l.on("click", () => openRef.current(props));
      },
    }).addTo(map);
    data.features.forEach((f) => layer.addData({ type: "Feature", geometry: f.geometry, properties: f } as GeoJSON.Feature));
    layerRef.current = layer;
    const bounds = layer.getBounds();
    if (bounds.isValid()) map.fitBounds(bounds, { padding: [24, 24], maxZoom: MAX_ZOOM });
  }, [data]);

  const meta = data?.meta;
  return (
    <div className="entity-map-view">
      {error && <div className="entity-list-panel-error">{(error as Error).message}</div>}
      {meta && meta.withoutGeometry > 0 && (
        <Message severity="info" className="entity-map-notice" text={t("list.mapWithoutGeometry", { count: meta.withoutGeometry })} />
      )}
      {meta?.truncated && (
        <Message severity="warn" className="entity-map-notice" text={t("list.mapTruncated", { shown: meta.shown + meta.withoutGeometry, total: meta.total })} />
      )}
      {meta && meta.shown === 0 && !meta.truncated && meta.withoutGeometry === 0 && (
        <Message severity="info" className="entity-map-notice" text={t("list.mapEmpty")} />
      )}
      <div className="entity-map-frame">
        <div ref={containerRef} className="entity-map-canvas" />
        {isLoading && <Skeleton className="entity-map-loading" height="100%" borderRadius="0" />}
      </div>
    </div>
  );
}
