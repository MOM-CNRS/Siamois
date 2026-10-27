import {createContext, useContext} from "react";

// True for the main pane while an overview is open next to it: its half of the screen is narrower, so its
// toolbar folds the secondary actions into the "…" menu, as the overview's own does.
const OverviewOpenContext = createContext(false);

export const OverviewOpenProvider = OverviewOpenContext.Provider;

export function useOverviewOpen(): boolean {
  return useContext(OverviewOpenContext);
}
