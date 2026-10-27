import {createContext, useContext} from "react";
import type {FieldResource} from "./types";

/**
 * What a field's label menu can ask of the fiche it is in: to open that field in the project's configuration.
 * Absent when the user may not configure the project, or off a fiche of a configurable type.
 */
export interface FieldConfigActions {
  configure: (field: FieldResource) => void;
}

const FieldConfigContext = createContext<FieldConfigActions | undefined>(undefined);

export const FieldConfigProvider = FieldConfigContext.Provider;

export function useFieldConfigActions(): FieldConfigActions | undefined {
  return useContext(FieldConfigContext);
}
