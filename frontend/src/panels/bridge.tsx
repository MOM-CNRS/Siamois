import { createContext, useContext, type ReactNode } from "react";
import type { PanelBridge } from "../mountOptions";

// MountOptions.bridge, for any panel below App — a detail panel's settings button needs
// openProjectSettings without every intermediate component threading it through as a prop.
// Same approach as WriteModeProvider.
const BridgeContext = createContext<PanelBridge>({});

export function BridgeProvider({ value, children }: { value: PanelBridge | undefined; children: ReactNode }) {
  return <BridgeContext.Provider value={value ?? {}}>{children}</BridgeContext.Provider>;
}

export function useBridge(): PanelBridge {
  return useContext(BridgeContext);
}
