import { useCallback, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode } from "react";

// Where the main pane's share of the width is remembered (this browser only).
const RATIO_STORAGE_KEY = "siamois.main-panel.split-ratio";
const DEFAULT_RATIO = 0.6;
const MIN_RATIO = 0.2;
const MAX_RATIO = 0.8;

function clampRatio(ratio: number): number {
  return Math.min(MAX_RATIO, Math.max(MIN_RATIO, ratio));
}

function loadRatio(): number {
  try {
    const stored = Number(window.localStorage.getItem(RATIO_STORAGE_KEY));
    return stored > 0 ? clampRatio(stored) : DEFAULT_RATIO;
  } catch {
    return DEFAULT_RATIO;
  }
}

function saveRatio(ratio: number): void {
  try {
    window.localStorage.setItem(RATIO_STORAGE_KEY, String(ratio));
  } catch {
    // Storage blocked (private window…): the ratio just isn't remembered.
  }
}

export interface PaneSplitProps {
  // The main pane, always rendered at the same place in the tree: opening or closing the overview
  // must never remount it, or every list/fiche below loses its local state (page, sort, filters,
  // selection, scroll, active tab).
  main: ReactNode;
  mainClassName: string;
  mainStyle?: CSSProperties;
  // The overview pane, or null when none is open (no gutter either).
  overview: ReactNode | null;
  overviewClassName?: string;
  overviewStyle?: CSSProperties;
}

/**
 * The main/overview split. Replaces PrimeReact's Splitter, which had to be swapped in and out
 * (single pane ↔ Splitter) as the overview opened and closed — two different trees, so React
 * remounted the main pane each time. Here the overview pane and its gutter are only appended after
 * the main one. Keeps PrimeReact's own class names so the theme still paints the gutter.
 */
export function PaneSplit({ main, mainClassName, mainStyle, overview, overviewClassName, overviewStyle }: PaneSplitProps) {
  const [ratio, setRatio] = useState(loadRatio);
  const containerRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);

  const onPointerDown = useCallback((e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    setDragging(true);
  }, []);

  const onPointerMove = useCallback(
    (e: ReactPointerEvent<HTMLDivElement>) => {
      if (!dragging) return;
      const box = containerRef.current?.getBoundingClientRect();
      if (!box || box.width === 0) return;
      setRatio(clampRatio((e.clientX - box.left) / box.width));
    },
    [dragging],
  );

  const onPointerUp = useCallback(
    (e: ReactPointerEvent<HTMLDivElement>) => {
      if (!dragging) return;
      e.currentTarget.releasePointerCapture(e.pointerId);
      setDragging(false);
      saveRatio(ratio);
    },
    [dragging, ratio],
  );

  const hasOverview = overview != null;

  return (
    <div
      ref={containerRef}
      className={`pane-split p-splitter p-component p-splitter-horizontal${dragging ? " p-splitter-resizing" : ""}`}
      style={{ height: "100%", display: "flex", flexWrap: "nowrap", minHeight: 0, border: "none", borderRadius: 0 }}
    >
      <div
        className={`p-splitter-panel ${mainClassName}`}
        style={{
          ...mainStyle,
          display: "flex",
          flexDirection: "column",
          minWidth: 0,
          minHeight: 0,
          flex: hasOverview ? `0 0 calc(${ratio * 100}% - 2px)` : "1 1 100%",
        }}
      >
        {main}
      </div>
      {hasOverview && (
        <>
          <div
            className="p-splitter-gutter pane-split-gutter"
            role="separator"
            aria-orientation="vertical"
            style={{ flex: "0 0 4px", cursor: "col-resize", display: "flex", alignItems: "center", justifyContent: "center", touchAction: "none" }}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
          >
            <div className="p-splitter-gutter-handle" />
          </div>
          <div
            className={`p-splitter-panel ${overviewClassName ?? ""}`}
            style={{ ...overviewStyle, display: "flex", flexDirection: "column", minWidth: 0, minHeight: 0, flex: "1 1 0" }}
          >
            {overview}
          </div>
        </>
      )}
    </div>
  );
}
