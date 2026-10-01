import { resolveValueBinding, unwrapAnswer, type FieldResource } from "./types";

/**
 * A field's value for the rules: through its binding when the catalog knows it (system fields read
 * their own property), else straight from the answers map — a rule may read a field the layout
 * doesn't place.
 */
export function valueOfField(entity: unknown, fields: Record<string, FieldResource>, id: string): unknown {
  const field = fields[id];
  if (field) return resolveValueBinding(field).read(entity);
  const answers = (entity as { answers?: Record<string, unknown> } | null)?.answers;
  return answers ? unwrapAnswer(answers[id]) : undefined;
}
