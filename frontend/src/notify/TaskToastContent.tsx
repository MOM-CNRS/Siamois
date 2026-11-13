import {useState, useSyncExternalStore} from "react";
import {Button} from "primereact/button";
import {ProgressBar} from "primereact/progressbar";
import {t} from "../i18n";

/** How far a task has got: what the task's toast shows. */
export interface TaskProgress {
  done: number;
  total: number;
}

/** The progress of one task, written by whoever runs it and read by its toast. */
export interface TaskProgressStore {
  get: () => TaskProgress;
  set: (progress: TaskProgress) => void;
  subscribe: (listener: () => void) => () => void;
}

/**
 * A progress store starting at nothing done.
 */
export function createTaskProgressStore(): TaskProgressStore {
  let current: TaskProgress = { done: 0, total: 0 };
  const listeners = new Set<() => void>();
  return {
    get: () => current,
    set: (progress) => {
      current = progress;
      listeners.forEach((listener) => listener());
    },
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

/**
 * The body of a task's toast: what runs, a progress bar, and how many items are done — redrawn as the
 * store moves, without the toast itself being replaced.
 */
export function TaskToastContent({ label, store, onCancel }: { label: string; store: TaskProgressStore; onCancel?: () => void }) {
  const { done, total } = useSyncExternalStore(store.subscribe, store.get);
  const [stopping, setStopping] = useState(false);
  const percent = total > 0 ? Math.round((done * 100) / total) : 0;
  return (
    <div className="task-toast" role="status" aria-live="polite">
      <div className="task-toast-label">
        <i className="pi pi-spin pi-spinner" aria-hidden="true" /> {label}
      </div>
      <ProgressBar value={percent} showValue={false} className="task-toast-bar" />
      <div className="task-toast-footer">
        <small className="task-toast-count">{total > 0 ? t("notify.taskProgress", { done, total, percent }) : t("notify.taskStarting")}</small>
        {onCancel && (
          <Button
            type="button"
            label={stopping ? t("notify.taskStopping") : t("notify.taskStop")}
            icon="bi bi-stop-circle"
            size="small"
            text
            severity="danger"
            disabled={stopping}
            onClick={() => {
              setStopping(true);
              onCancel();
            }}
          />
        )}
      </div>
    </div>
  );
}
