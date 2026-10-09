import type { FieldResource } from "./types";

/**
 * An entity's type is bound to `type` on every table. It is a system field of the
 * form, but it is not edited like the others: the fiche's header holds it (and offers only the types the
 * project declared), so the form itself leaves it out.
 */
export function isTypeField(field: FieldResource): boolean {
  return field.valueBinding === "type";
}
