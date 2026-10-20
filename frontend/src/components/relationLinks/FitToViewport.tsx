import { useLayoutEffect, useRef, useState, type ReactNode } from "react";

const MARGIN = 16;
const MIN_HEIGHT = 220;

// Keeps what it holds inside the window: its height is capped to the room left below its top edge,
// and it scrolls past that. An overlay is placed once, when it opens, so a form that grows afterwards
// (a list unfolding) would otherwise run off the bottom of the screen.
export function FitToViewport({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [maxHeight, setMaxHeight] = useState<number | undefined>(undefined);

  useLayoutEffect(() => {
    const fit = () => {
      const top = ref.current?.getBoundingClientRect().top ?? 0;
      setMaxHeight(Math.max(MIN_HEIGHT, window.innerHeight - top - MARGIN));
    };
    fit();
    window.addEventListener("resize", fit);
    // The overlay is placed after this mounts, and its content grows: measure again each time it changes.
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(fit);
    if (ref.current) observer?.observe(ref.current);
    return () => {
      window.removeEventListener("resize", fit);
      observer?.disconnect();
    };
  }, []);

  return <div ref={ref} className="sia-graph-fit" style={{ maxHeight }}>{children}</div>;
}
