import type { ReactNode } from "react";
import type { FieldResource } from "./types";

// The "answerType → component" pattern validated on the RU branch's fieldRegistry.tsx (plan
// §3), re-implemented fresh here (no dependency on that unmerged branch).
export interface FieldRendererProps {
  field: FieldResource;
  value: unknown;
  readOnly: boolean;
  required: boolean;
  onChange: (value: unknown) => void;
  // Needed only by renderers backed by an org-scoped async option source (SELECT_ONE_FROM_FIELD_CODE,
  // SELECT_MULTIPLE_FROM_FIELD_CODE, SELECT_ONE_SPATIAL_UNIT) to build their loader
  // (fields/optionSources.ts). Renderers that don't need one ignore it.
  organizationId?: number;
}

export type FieldRenderer = (props: FieldRendererProps) => ReactNode;

const renderers = new Map<string, FieldRenderer>();
let fallback: FieldRenderer | null = null;

export function registerFieldRenderer(answerType: string, renderer: FieldRenderer): void {
  renderers.set(answerType, renderer);
}

export function registerFallbackFieldRenderer(renderer: FieldRenderer): void {
  fallback = renderer;
}

// Lets a consumer tell "has a real renderer" apart from "will silently fall back to read-only
// display" (plan §8 phase 6 — the Project fiche uses this to decide whether a schema-driven
// field can go into edit mode at all, rather than presenting a fake-editable control).
export function hasFieldRenderer(answerType: string): boolean {
  return renderers.has(answerType);
}

export function getFieldRenderer(answerType: string): FieldRenderer {
  const renderer = renderers.get(answerType);
  if (renderer) return renderer;
  if (fallback) return fallback;
  throw new Error(
    `No field renderer registered for answerType "${answerType}" and no fallback configured`,
  );
}
