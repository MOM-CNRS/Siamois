import {createContext, type ReactNode, useContext, useEffect, useMemo, useRef} from "react";
import {Toast, type ToastMessage} from "primereact/toast";
import {t} from "../i18n";
import {createTaskProgressStore, TaskToastContent} from "./TaskToastContent";

/** The toast of a task running in the background, kept up while it runs. */
export interface TaskToast {
  // How far the task has got.
  progress: (done: number, total: number) => void;
  // Takes the toast away (the task's outcome is told by a toast of its own).
  close: () => void;
}

export interface Notify {
  // `life`: how long the toast stays, in ms, when not the severity's usual time.
  error: (message: string, life?: number) => void;
  success: (message: string, life?: number) => void;
  warn: (message: string, life?: number) => void;
  info: (message: string, life?: number) => void;
  // A toast that stays while a task runs, showing its progress.
  task: (label: string) => TaskToast;
}

const NO_TASK_TOAST: TaskToast = { progress: () => {}, close: () => {} };

// Outside a provider (a panel mounted alone in a test) notifications go nowhere.
const NotifyContext = createContext<Notify>({
  error: () => {},
  success: () => {},
  warn: () => {},
  info: () => {},
  task: () => NO_TASK_TOAST,
});

/** One PrimeReact Toast for the whole app: what failed away from any form (a row action, a bookmark…). */
export function NotifyProvider({ children }: { children: ReactNode }) {
  const toast = useRef<Toast>(null);
  const notify = useMemo<Notify>(
    () => ({
      error: (detail, life = 6000) => toast.current?.show({ severity: "error", summary: t("notify.error"), detail, life }),
      success: (detail, life = 3000) => toast.current?.show({ severity: "success", detail, life }),
      warn: (detail, life = 6000) => toast.current?.show({ severity: "warn", detail, life }),
      info: (detail, life = 4000) => toast.current?.show({ severity: "info", detail, life }),
      task: (label) => {
        const store = createTaskProgressStore();
        const message: ToastMessage = {
          severity: "info",
          sticky: true,
          closable: false,
          content: () => <TaskToastContent label={label} store={store} />,
        };
        toast.current?.show(message);
        return {
          progress: (done, total) => store.set({ done, total }),
          close: () => toast.current?.remove(message),
        };
      },
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

type Severity = Exclude<keyof Notify, "task">;

/** Toasts `message` each time it changes to a non-empty text (the state of a form or a query that reports a failure). */
export function useToastOnChange(message: string | null | undefined, severity: Severity = "error"): void {
  const notify = useNotify();
  useEffect(() => {
    if (message) notify[severity](message);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [message]);
}
