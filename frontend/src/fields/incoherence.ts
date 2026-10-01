import type { IncoherenceReason } from "../rules";
import { t } from "../i18n";

const CONSTRAINT_MESSAGE = {
  GT: "cell.boundMinExcl",
  GTE: "cell.boundMinIncl",
  LT: "cell.boundMaxExcl",
  LTE: "cell.boundMaxIncl",
} as const;

/** Why a stored value no longer fits the form's rules, in words. */
export function describeIncoherence(reason: IncoherenceReason, labelOf: (fieldId: string) => string): string {
  switch (reason.kind) {
    case "DISABLED_WITH_VALUE":
      return t("incoherence.disabled");
    case "OUT_OF_OPTIONS":
      return t("incoherence.outOfOptions");
    case "CONSTRAINT":
      return t(CONSTRAINT_MESSAGE[reason.op as keyof typeof CONSTRAINT_MESSAGE], { label: labelOf(reason.otherFieldId) });
  }
}
