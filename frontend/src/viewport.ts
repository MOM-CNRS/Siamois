import {useEffect, useState} from "react";

// A phone-width window: the overview pane has no room next to the main one, so entities open in the main pane
// and the toolbar folds into its "…" menu.
export const MOBILE_QUERY = "(max-width: 768px)";

export function isMobileViewport(): boolean {
  return window.matchMedia?.(MOBILE_QUERY).matches ?? false;
}

export function useIsMobile(): boolean {
  const [mobile, setMobile] = useState(isMobileViewport);
  useEffect(() => {
    const query = window.matchMedia?.(MOBILE_QUERY);
    if (!query) return;
    const onChange = () => setMobile(query.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);
  return mobile;
}
