import { useEffect, useRef, type ReactNode } from "react";
import { OverlayPanel } from "primereact/overlaypanel";
import { useIsMobile } from "../../viewport";
import { BottomSheet } from "./BottomSheet";

export interface AnchoredFormOverlayProps {
  // The element the overlay opens next to (the button or field the creation was started from);
  // null = closed. Every creation opens next to what started it, never in a modal.
  anchor: HTMLElement | null;
  ariaLabel: string;
  onHide: () => void;
  // Rendered only while open, so each opening starts from a fresh form; `close` hides the overlay.
  children: (close: () => void) => ReactNode;
}

/**
 * The overlay every creation-like form lives in (an entity's create form, the structure
 * duplication): anchored on what started it, closed by `anchor` going back to null.
 */
export function AnchoredFormOverlay({ anchor, ariaLabel, onHide, children }: AnchoredFormOverlayProps) {
  const overlayRef = useRef<OverlayPanel>(null);
  const mobile = useIsMobile();

  useEffect(() => {
    if (mobile) return;
    if (anchor) overlayRef.current?.show(null as never, anchor);
    else overlayRef.current?.hide();
  }, [anchor, mobile]);

  const close = () => (mobile ? onHide() : overlayRef.current?.hide());

  // Phone width: a drawer from the bottom instead of an overlay anchored to the button.
  if (mobile) {
    return (
      <div className="sia-contents">
        <BottomSheet visible={anchor != null} onHide={onHide} ariaLabel={ariaLabel} className="create-entity-sheet">
          {anchor && children(close)}
        </BottomSheet>
      </div>
    );
  }

  return (
    // Keys typed in the form must not bubble through the React tree to whatever hosts it (a cell
    // editor cancels its edit on Escape): the overlay is portalled out of its DOM box, not out of
    // its React tree.
    <div className="sia-contents" onKeyDown={(e) => e.stopPropagation()}>
      <OverlayPanel
        ref={overlayRef}
        className="entity-list-panel-create-overlay create-entity-overlay"
        onHide={onHide}
        aria-label={ariaLabel}
      >
        {anchor && children(close)}
      </OverlayPanel>
    </div>
  );
}
