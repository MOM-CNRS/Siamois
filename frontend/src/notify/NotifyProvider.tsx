import { createContext, useContext, useMemo, useRef, type ReactNode } from "react";
import { Toast } from "primereact/toast";
import { t } from "../i18n";

export interface Notify {
  error: (message: string) => void;
  success: (message: string) => void;
}

// Outside a provider (a panel mounted alone in a test) notifications go nowhere.
const NotifyContext = createContext<Notify>({ error: () => {}, success: () => {} });

/** One PrimeReact Toast for the whole app: what failed away from any form (a row action, a bookmark…). */
export function NotifyProvider({ children }: { children: ReactNode }) {
  const toast = useRef<Toast>(null);
  const notify = useMemo<Notify>(
    () => ({
      error: (detail) => toast.current?.show({ severity: "error", summary: t("notify.error"), detail, life: 6000 }),
      success: (detail) => toast.current?.show({ severity: "success", detail, life: 3000 }),
    }),
    [],
  );
  return (
    <NotifyContext.Provider value={notify}>
      {children}
      <Toast ref={toast} position="top-right" />
    </NotifyContext.Provider>
  );
}

export function useNotify(): Notify {
  return useContext(NotifyContext);
}
