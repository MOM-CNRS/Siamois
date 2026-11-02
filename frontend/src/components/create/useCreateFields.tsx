import {type ReactNode, useCallback, useMemo, useState} from "react";
import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../api/queryKeys";
import {getEffectiveForm} from "../../entities/shared/typeCatalog";
import type {CreatableKind} from "../../entities/shared/types";
import {getFieldRenderer} from "../../fields/registry";
import {type AnswerInputBody, type FieldResource, toAnswerInput} from "../../fields/types";
import {formatDateAnswer} from "../../fields/dateAnswer";
import {CreateFormField} from "./CreateFormShell";

const STORAGE_PREFIX = "siamois.create.last.";

// What was submitted last, by value binding. A browser-side convenience (like JSF's bean kept in the
// session): unreadable or unwritable storage just means nothing is remembered.
function readRemembered(kind: CreatableKind): Record<string, unknown> {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + kind);
    const parsed = raw ? JSON.parse(raw) : null;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function writeRemembered(kind: CreatableKind, values: Record<string, unknown>) {
  try {
    localStorage.setItem(STORAGE_PREFIX + kind, JSON.stringify(values));
  } catch {
    // Storage full or blocked: the next form simply starts empty.
  }
}

// A date field starts at the current moment, never at a remembered value.
function nowOf(field: FieldResource): string {
  return formatDateAnswer(new Date(), field.constraints?.showTime === true);
}

function isEmpty(value: unknown): boolean {
  return value == null || value === "" || (Array.isArray(value) && value.length === 0);
}

/**
 * The optional, pre-filled fields of a creation form: the fields of the entity's form (for the picked
 * type) bound to `bindings`, edited with their usual renderers. Each starts with the value of the last
 * submission of this kind (`remember`, called once a creation succeeded), except the `nowBindings`
 * (dates), which always start at now and are never remembered. A field left empty is not sent, so the
 * server's own defaults still apply.
 *
 * @param segment the project catalog holding the entity's typed forms (e.g. "recording-unit-types")
 * @param bindings the valueBinding of each field to offer, in display order
 * @param nowBindings those of `bindings` that are dates defaulting to now
 */
export function useCreateFields({
  kind,
  segment,
  bindings,
  nowBindings = [],
  projectId,
  typeId,
  organizationId,
}: {
  kind: CreatableKind;
  segment: string;
  bindings: readonly string[];
  nowBindings?: readonly string[];
  projectId: string | null | undefined;
  typeId: string | null | undefined;
  organizationId?: number;
}): { fields: ReactNode; answers: Record<string, AnswerInputBody>; remember: () => void } {
  const [values, setValues] = useState<Record<string, unknown>>(() => readRemembered(kind));

  const form = useQuery({
    queryKey: queryKeys.effectiveForm(segment, projectId, typeId ?? null),
    queryFn: () => getEffectiveForm(segment, projectId as string, typeId),
    enabled: projectId != null && typeId != null,
  });

  const offered = useMemo(() => {
    const all = Object.values(form.data?.fields ?? {});
    return bindings.flatMap((binding) => {
      const field = all.find((f) => f.valueBinding === binding);
      return field ? [{ binding, field }] : [];
    });
  }, [form.data, bindings]);

  // A date the user has not touched is now; one they cleared (key present, null) stays empty.
  const valueOf = useCallback(
    (binding: string, field: FieldResource) =>
      binding in values ? values[binding] : nowBindings.includes(binding) ? nowOf(field) : undefined,
    [values, nowBindings],
  );

  const answers = useMemo(() => {
    const body: Record<string, AnswerInputBody> = {};
    for (const { binding, field } of offered) {
      const value = valueOf(binding, field);
      if (!isEmpty(value)) body[field.id] = toAnswerInput(field, value);
    }
    return body;
  }, [offered, valueOf]);

  const remember = useCallback(() => {
    const next = { ...readRemembered(kind) };
    for (const { binding } of offered) {
      if (nowBindings.includes(binding)) delete next[binding];
      else if (isEmpty(values[binding])) delete next[binding];
      else next[binding] = values[binding];
    }
    writeRemembered(kind, next);
  }, [kind, offered, values, nowBindings]);

  const fields = offered.map(({ binding, field }) => {
    const Renderer = getFieldRenderer(field.answerType);
    return (
      <CreateFormField key={field.id} label={field.label}>
        <Renderer
          field={field}
          value={valueOf(binding, field) ?? null}
          readOnly={false}
          required={false}
          organizationId={organizationId}
          context={{ organizationId, projectId: projectId ?? undefined, typeConceptId: typeId ?? undefined }}
          onChange={(value) => setValues((current) => ({ ...current, [binding]: value }))}
        />
      </CreateFormField>
    );
  });

  return { fields, answers, remember };
}
