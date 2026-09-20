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

export function getFieldRenderer(answerType: string): FieldRenderer {
  const renderer = renderers.get(answerType);
  if (renderer) return renderer;
  if (fallback) return fallback;
  throw new Error(
    `No field renderer registered for answerType "${answerType}" and no fallback configured`,
  );
}
