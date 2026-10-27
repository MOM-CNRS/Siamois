import {createContext, type ReactNode, useContext, useEffect, useMemo, useRef} from "react";
import {Toast} from "primereact/toast";
import {t} from "../i18n";

export interface Notify {
  error: (message: string) => void;
  success: (message: string) => void;
  warn: (message: string) => void;
  info: (message: string) => void;
}

// Outside a provider (a panel mounted alone in a test) notifications go nowhere.
const NotifyContext = createContext<Notify>({ error: () => {}, success: () => {}, warn: () => {}, info: () => {} });

/** One PrimeReact Toast for the whole app: what failed away from any form (a row action, a bookmark…). */
export function NotifyProvider({ children }: { children: ReactNode }) {
  const toast = useRef<Toast>(null);
  const notify = useMemo<Notify>(
    () => ({
      error: (detail) => toast.current?.show({ severity: "error", summary: t("notify.error"), detail, life: 6000 }),
      success: (detail) => toast.current?.show({ severity: "success", detail, life: 3000 }),
      warn: (detail) => toast.current?.show({ severity: "warn", detail, life: 6000 }),
      info: (detail) => toast.current?.show({ severity: "info", detail, life: 4000 }),
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

type Severity = keyof Notify;

/** Toasts `message` each time it changes to a non-empty text (the state of a form or a query that reports a failure). */
export function useToastOnChange(message: string | null | undefined, severity: Severity = "error"): void {
  const notify = useNotify();
  useEffect(() => {
    if (message) notify[severity](message);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [message]);
}
