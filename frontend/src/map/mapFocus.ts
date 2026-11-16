import {useEffect, useRef} from "react";

// A channel from what shows an entity (its fiche's geometry field) to a map that shows it too (the
// list's map view): the field asks, a map that can answers by framing the entity. The two live in
// different panes with no common parent but App, so they meet here rather than through a context.

/** Frames the entity on the map and says so; false when this map does not show it. */
export type MapFocusTarget = (entityType: string, id: string | number) => boolean;

const targets = new Set<MapFocusTarget>();

/** Asks the mounted maps to frame an entity; says whether one did. */
export function focusOnMap(entityType: string, id: string | number): boolean {
  for (const target of targets) {
    if (target(entityType, id)) return true;
  }
  return false;
}

/** Registers a map as a focus target for as long as the component is mounted; the handler is read at call time. */
export function useMapFocusTarget(target: MapFocusTarget): void {
  const targetRef = useRef(target);
  targetRef.current = target;
  useEffect(() => {
    const registered: MapFocusTarget = (entityType, id) => targetRef.current(entityType, id);
    targets.add(registered);
    return () => {
      targets.delete(registered);
    };
  }, []);
}
