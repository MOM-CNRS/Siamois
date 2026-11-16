import {useRef} from "react";
import "leaflet/dist/leaflet.css";
import type {MapFeature} from "../entities/shared/listApi";
import {SELECTED_LOOK} from "./featureLook";
import {fitLayer} from "./leafletMap";
import {useFeatureLayer} from "./useFeatureLayer";
import {useLeafletMap} from "./useLeafletMap";

/** A small map framing a few features, drawn like the list map's selection (e.g. a fiche's geometry). */
export function FeatureMap({ features, className }: Readonly<{ features: readonly MapFeature[]; className?: string }>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const map = useLeafletMap(containerRef);
  useFeatureLayer(map, features, SELECTED_LOOK, { onDrawn: (m, layer) => fitLayer(m, layer) });
  return <div ref={containerRef} className={className} />;
}
