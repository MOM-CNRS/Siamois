import {type PointerEvent, type ReactNode, useEffect, useRef, useState} from "react";
import {Sidebar} from "primereact/sidebar";

export interface BottomSheetProps {
  visible: boolean;
  onHide: () => void;
  // Names the drawer for assistive technology.
  ariaLabel: string;
  className?: string;
  children: ReactNode;
}

// A drag of at least this many pixels (down to close, up to expand) counts as a gesture.
const DRAG_THRESHOLD = 70;

/**
 * A drawer that opens from the bottom of the screen — what an overlay becomes on a phone, where one anchored to its
 * field ends up under the keyboard or off the screen. Sized to its content, up to most of the visible height. Its
 * handle is dragged: down closes it (or brings an expanded one back), up expands it to nearly the whole page.
 */
export function BottomSheet({ visible, onHide, ariaLabel, className, children }: BottomSheetProps) {
  const [expanded, setExpanded] = useState(false);
  const drag = useRef<{ startY: number; sheet: HTMLElement } | null>(null);

  // Each opening starts at the content's own height.
  useEffect(() => {
    if (!visible) setExpanded(false);
  }, [visible]);

  function onPointerDown(e: PointerEvent<HTMLDivElement>) {
    const sheet = e.currentTarget.closest<HTMLElement>(".p-sidebar");
    if (!sheet) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { startY: e.clientY, sheet };
    sheet.style.transition = "none";
  }

  function onPointerMove(e: PointerEvent<HTMLDivElement>) {
    if (!drag.current) return;
    const dy = e.clientY - drag.current.startY;
    // Only following the finger downwards: upwards the sheet grows on release.
    drag.current.sheet.style.transform = dy > 0 ? `translateY(${dy}px)` : "";
  }

  function onPointerUp(e: PointerEvent<HTMLDivElement>) {
    const current = drag.current;
    drag.current = null;
    if (!current) return;
    const dy = e.clientY - current.startY;
    current.sheet.style.transition = "";
    current.sheet.style.transform = "";
    if (dy > DRAG_THRESHOLD) {
      if (expanded) setExpanded(false);
      else onHide();
    } else if (dy < -DRAG_THRESHOLD && !expanded) {
      setExpanded(true);
    }
  }

  const handle = (
    <div
      className="bottom-sheet-handle"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      <span className="bottom-sheet-grip" />
    </div>
  );

  return (
    <Sidebar
      visible={visible}
      position="bottom"
      onHide={onHide}
      className={`bottom-sheet${expanded ? " is-expanded" : ""}${className ? ` ${className}` : ""}`}
      aria-label={ariaLabel}
      header={handle}
      // Keys typed in the sheet must not reach whatever hosts it in the React tree (a cell editor cancels on Escape).
      onKeyDown={(e) => e.stopPropagation()}
      blockScroll
    >
      {children}
    </Sidebar>
  );
}
