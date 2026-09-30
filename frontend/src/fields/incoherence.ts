import type { IncoherenceReason } from "../rules";

const CONSTRAINT_TEXT: Record<string, string> = {
  GT: "doit être supérieure à",
  GTE: "doit être supérieure ou égale à",
  LT: "doit être inférieure à",
  LTE: "doit être inférieure ou égale à",
};

/** Why a stored value no longer fits the form's rules, in words. */
export function describeIncoherence(reason: IncoherenceReason, labelOf: (fieldId: string) => string): string {
  switch (reason.kind) {
    case "DISABLED_WITH_VALUE":
      return "Ce champ ne s'applique plus avec les réponses actuelles, mais contient une valeur.";
    case "OUT_OF_OPTIONS":
      return "Cette valeur ne fait plus partie des choix possibles.";
    case "CONSTRAINT":
      return `La valeur ${CONSTRAINT_TEXT[reason.op]} « ${labelOf(reason.otherFieldId)} ».`;
  }
}
