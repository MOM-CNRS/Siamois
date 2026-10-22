import { useEffect, useRef } from "react";

/**
 * Gives the focus back to what opened an overlay (the cell or the chip clicked) once it closes, so the keyboard
 * carries on from there. The opener is read while the overlay's first render happens — before it can take the
 * focus itself — and it is only refocused when nothing else holds the focus by then (a click on another cell,
 * which opens the next overlay, keeps it).
 */
export function useRestoreFocus(open: boolean): void {
  const opener = useRef<HTMLElement | null>(null);
  const wasOpen = useRef(false);
  if (open && !wasOpen.current) {
    const active = document.activeElement;
    opener.current = active instanceof HTMLElement && active !== document.body ? active : null;
  }
  wasOpen.current = open;

  useEffect(() => {
    if (open) return;
    const target = opener.current;
    opener.current = null;
    if (!target) return;
    const timer = setTimeout(() => {
      const active = document.activeElement;
      if (target.isConnected && (active == null || active === document.body)) target.focus();
    }, 0);
    return () => clearTimeout(timer);
  }, [open]);
}
