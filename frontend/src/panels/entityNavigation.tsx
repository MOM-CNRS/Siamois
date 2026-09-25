import { createContext, useContext, type ReactNode } from "react";

// Opens an entity's full fiche from anywhere below App — a field's reference chip needs it without
// every panel, form and edit overlay in between threading `onNavigate` through as a prop. Same
// approach as WriteModeProvider / BridgeProvider. Undefined outside App (tests, the theme
// harness): references then just aren't links.
const EntityNavigationContext = createContext<((entityType: string, id: string | number) => void) | undefined>(undefined);

export function EntityNavigationProvider({
  value,
  children,
}: {
  value: ((entityType: string, id: string | number) => void) | undefined;
  children: ReactNode;
}) {
  return <EntityNavigationContext.Provider value={value}>{children}</EntityNavigationContext.Provider>;
}

export function useOpenEntity(): ((entityType: string, id: string | number) => void) | undefined {
  return useContext(EntityNavigationContext);
}
