import { createContext, useContext } from "react";
import type { FilterValue } from "../entities/types";

/**
 * What a list shown as a tab of a fiche can ask of that fiche: to keep the list it is showing, with its filters,
 * as a tab of the project's configuration. Absent when the user may not configure the fiche (or off a fiche).
 */
export interface FicheTabsActions {
  saveAsTab: (target: string, filters: Record<string, FilterValue>) => void;
}

const FicheTabsContext = createContext<FicheTabsActions | undefined>(undefined);

export const FicheTabsProvider = FicheTabsContext.Provider;

export function useFicheTabsActions(): FicheTabsActions | undefined {
  return useContext(FicheTabsContext);
}
