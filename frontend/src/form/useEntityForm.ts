import { useCallback, useMemo, useRef, useState } from "react";
import { apiFetch, ApiError } from "../api/client";
import { toAnswerInput, toLocalValue, type LocalValue } from "./answerCodec";
import { EnabledRulesEngine, collectColumns } from "./rulesEngine";
import type { FieldAnswer, FieldAnswerType, FormLayout } from "./schema";

export type FieldStatus = "idle" | "saving" | "saved" | "error";

interface PatchResponseData {
  syncRevision: number;
}
interface PatchResponse {
  data: PatchResponseData;
}

export interface UseEntityFormOptions {
  recordingUnitId: number;
  layout: FormLayout;
  initialAnswers: Record<string, FieldAnswer>;
  initialRevision: number;
  /** Debounce before a field edit is PATCHed, matching the "commit on settle" feel of the JSF autosave
   *  without firing a request per keystroke. */
  debounceMs?: number;
}

/**
 * Client-side orchestrator for one entity's form: local field state, the conditional-enable rules
 * engine (rulesEngine.ts), and debounced PATCH-per-field with optimistic-lock conflict detection —
 * the React analogue of EntityFormContext, minus the JSF-only "create new X inline" flows (phase 4/5).
 *
 * KNOWN LIMITATION: two different fields edited within the same debounce window each capture the
 * `expectedRevision` known at the time their own timer fires; if both land as separate PATCHes before
 * either response returns, the second can 409 spuriously even though there's no real conflict. Fine for
 * this phase (fields commit independently, same as JSF autosave); revisit if it proves annoying.
 */
export function useEntityForm({
  recordingUnitId,
  layout,
  initialAnswers,
  initialRevision,
  debounceMs = 600,
}: UseEntityFormOptions) {
  const [values, setValues] = useState<Record<string, LocalValue>>(() => {
    const initial: Record<string, LocalValue> = {};
    for (const [fieldId, answer] of Object.entries(initialAnswers)) {
      initial[fieldId] = toLocalValue(answer);
    }
    return initial;
  });
  const [answerTypes] = useState<Record<string, FieldAnswerType>>(() => {
    const types: Record<string, FieldAnswerType> = {};
    for (const [fieldId, answer] of Object.entries(initialAnswers)) {
      types[fieldId] = answer.answerType;
    }
    return types;
  });
  const [status, setStatus] = useState<Record<string, FieldStatus>>({});
  const [revision, setRevision] = useState(initialRevision);
  const [conflict, setConflict] = useState(false);

  const rulesEngine = useMemo(() => new EnabledRulesEngine(collectColumns(layout)), [layout]);

  const timers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const valuesRef = useRef(values);
  valuesRef.current = values;
  const revisionRef = useRef(revision);
  revisionRef.current = revision;

  const lookup = useCallback((fieldId: number) => valuesRef.current[String(fieldId)] ?? null, []);
  const isEnabled = useCallback((fieldId: number) => rulesEngine.isEnabled(fieldId, lookup), [rulesEngine, lookup]);

  const save = useCallback(
    async (fieldKey: string, value: LocalValue) => {
      const answerType = answerTypes[fieldKey];
      if (!answerType) return;
      setStatus((s) => ({ ...s, [fieldKey]: "saving" }));
      try {
        const response = await apiFetch<PatchResponse>(`/recording-units/${recordingUnitId}`, {
          method: "PATCH",
          body: JSON.stringify({
            expectedRevision: revisionRef.current,
            answers: { [fieldKey]: toAnswerInput(value, answerType) },
          }),
        });
        setRevision(response.data.syncRevision);
        setStatus((s) => ({ ...s, [fieldKey]: "saved" }));
      } catch (err) {
        if (err instanceof ApiError && err.status === 409) {
          setConflict(true);
        }
        setStatus((s) => ({ ...s, [fieldKey]: "error" }));
      }
    },
    [answerTypes, recordingUnitId],
  );

  const setValue = useCallback(
    (fieldId: number, value: LocalValue) => {
      const key = String(fieldId);
      setValues((v) => ({ ...v, [key]: value }));

      const existingTimer = timers.current[key];
      if (existingTimer) clearTimeout(existingTimer);
      timers.current[key] = setTimeout(() => {
        void save(key, value);
      }, debounceMs);
    },
    [debounceMs, save],
  );

  const getValue = useCallback((fieldId: number): LocalValue => valuesRef.current[String(fieldId)] ?? null, []);
  const fieldStatus = useCallback((fieldId: number): FieldStatus => status[String(fieldId)] ?? "idle", [status]);

  /** Call after refetching the entity post-conflict, to reset local state to the server's version. */
  const resolveConflict = useCallback((freshAnswers: Record<string, FieldAnswer>, freshRevision: number) => {
    const nextValues: Record<string, LocalValue> = {};
    for (const [fieldId, answer] of Object.entries(freshAnswers)) {
      nextValues[fieldId] = toLocalValue(answer);
    }
    setValues(nextValues);
    setRevision(freshRevision);
    setStatus({});
    setConflict(false);
  }, []);

  return { getValue, setValue, fieldStatus, isEnabled, conflict, revision, resolveConflict };
}
