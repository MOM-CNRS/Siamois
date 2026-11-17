import {type RefObject, useEffect, useRef} from "react";
import type L from "leaflet";
import type {MapFeature} from "../entities/shared/listApi";
import type {FeatureLook} from "./featureLook";
import {createFeatureLayer} from "./leafletMap";

/**
 * Draws a set of features on the map in a look, redrawn when the set changes and removed on unmount.
 * `onDrawn` frames the new layer, `onOpen` answers a click; both are read when they are needed, so a
 * parent re-rendering with new callbacks does not redraw the layer. Returns the layer on the map, for
 * framing it later.
 */
export function useFeatureLayer(
  map: L.Map | null,
  features: readonly MapFeature[],
  look: FeatureLook,
  callbacks: { onOpen?: (feature: MapFeature) => void; onDrawn?: (map: L.Map, layer: L.GeoJSON) => void },
): RefObject<L.GeoJSON | null> {
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;
  const layerRef = useRef<L.GeoJSON | null>(null);

  useEffect(() => {
    if (!map) return;
    const layer = createFeatureLayer(features, look, (f) => callbacksRef.current.onOpen?.(f)).addTo(map);
    layerRef.current = layer;
    callbacksRef.current.onDrawn?.(map, layer);
    return () => {
      layer.remove();
      if (layerRef.current === layer) layerRef.current = null;
    };
  }, [map, features, look]);

  return layerRef;
}
