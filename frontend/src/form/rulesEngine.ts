/**
 * Client-side port of fr.siamois.ui.form.rules.{EnabledRulesEngine,EqCondition,NeqCondition,InCondition}
 * and fr.siamois.domain.services.form.FormService.{buildEnabledEngine,toCondition,toMatcher} plus
 * fr.siamois.ui.form.rules.ValueMatcherFactory. Must reproduce the server's exact semantics, including
 * its fail-closed and default-false fallback behaviors — this is what lets the React Details tab react
 * to field changes locally instead of round-tripping to the server on every keystroke.
 *
 * KNOWN GAP: the one implemented matcher (`forSelectOneFromFieldCode`) needs the *external* vocabulary/
 * concept ids (`vocabularyExtId`/`conceptExtId`) of the currently selected concept. The REST concept
 * resources this app consumes today (`ResolvedConceptResource`, from `/api/v1/projects/{id}/concepts`)
 * only expose `externalUrl` (an ARK), not those two ids split out. Until the backend exposes them (or
 * the client parses them out of the ARK), a rule that should evaluate true will instead fall through to
 * `false` here — same as every other matcher kind already does server-side. Flag this to product before
 * relying on conditional field visibility working for concept-based conditions in production.
 */
import type { EnabledWhenJson, EnabledWhenValueJson, CustomColUiDto } from "./schema";

/** FQCN server-side matcher discriminator — the only one FormService.toMatcher actually implements. */
const SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS =
  "fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectOneFromFieldAnswerCode";

interface ConceptExternalIds {
  vocabularyExtId?: string;
  conceptExtId?: string;
}

/** Matches a live answer value against one EnabledWhenValueJson. Mirrors ValueMatcherFactory. */
type ValueMatcher = (currentAnswer: unknown) => boolean;

function defaultMatcher(): ValueMatcher {
  // ValueMatcherFactory.defaultMatcher(): always false — any answerClass Java doesn't special-case
  // falls through to this, and we replicate that exactly rather than guessing at intent.
  return () => false;
}

function forSelectOneFromFieldCode(spec: unknown): ValueMatcher {
  const target = spec as ConceptExternalIds | null | undefined;
  return (currentAnswer) => {
    if (!target?.vocabularyExtId || !target?.conceptExtId) return false;
    if (!currentAnswer || typeof currentAnswer !== "object") return false;
    const cur = currentAnswer as ConceptExternalIds;
    return cur.vocabularyExtId === target.vocabularyExtId && cur.conceptExtId === target.conceptExtId;
  };
}

function toMatcher(v: EnabledWhenValueJson): ValueMatcher {
  if (v.answerClass === SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS) {
    return forSelectOneFromFieldCode(v.value);
  }
  return defaultMatcher();
}

/** Reads the current (possibly-just-edited) answer value for a given fieldId, for condition testing. */
export type AnswerLookup = (fieldId: number) => unknown;

interface Condition {
  /** The field this condition depends on (EnabledWhenJson.fieldId) — used for the reactive index. */
  dependsOnFieldId: number;
  test: (lookup: AnswerLookup) => boolean;
}

function toCondition(ew: EnabledWhenJson): Condition {
  const matchers = ew.values.map(toMatcher);
  switch (ew.op) {
    case "EQ": {
      const m = matchers[0] ?? defaultMatcher();
      return { dependsOnFieldId: ew.fieldId, test: (lookup) => m(lookup(ew.fieldId)) };
    }
    case "NEQ": {
      const m = matchers[0] ?? defaultMatcher();
      return { dependsOnFieldId: ew.fieldId, test: (lookup) => !m(lookup(ew.fieldId)) };
    }
    case "IN": {
      return {
        dependsOnFieldId: ew.fieldId,
        test: (lookup) => {
          const cur = lookup(ew.fieldId);
          if (cur === null || cur === undefined) return false; // InCondition: null current answer -> false
          return matchers.some((m) => m(cur));
        },
      };
    }
    default:
      // Unknown op (schema drift) — fail closed rather than silently enabling.
      return { dependsOnFieldId: ew.fieldId, test: () => false };
  }
}

/**
 * Client-side EnabledRulesEngine: one rule per column that has an `enabledWhen`, indexed both by the
 * column's own fieldId (for `isEnabled`) and by the fieldId it depends on (for `affectedByChange`,
 * mirroring the server's onAnswerChange reactive re-evaluation).
 */
export class EnabledRulesEngine {
  private readonly ruleByColumnField = new Map<number, Condition>();
  private readonly dependentsByField = new Map<number, Set<number>>();

  constructor(columns: readonly CustomColUiDto[]) {
    for (const col of columns) {
      if (col.fieldId == null || !col.enabledWhen) continue;
      const condition = toCondition(col.enabledWhen);
      this.ruleByColumnField.set(col.fieldId, condition);
      const existing = this.dependentsByField.get(condition.dependsOnFieldId);
      if (existing) {
        existing.add(col.fieldId);
      } else {
        this.dependentsByField.set(condition.dependsOnFieldId, new Set([col.fieldId]));
      }
    }
  }

  /** True if no rule applies (column always enabled) or the rule's condition currently holds. */
  isEnabled(columnFieldId: number, lookup: AnswerLookup): boolean {
    const rule = this.ruleByColumnField.get(columnFieldId);
    if (!rule) return true;
    try {
      return rule.test(lookup);
    } catch {
      // EnabledRulesEngine.safeTest: any evaluation error disables the column (fail-closed).
      return false;
    }
  }

  /** fieldIds whose enabled-state may have changed now that `changedFieldId`'s answer changed. */
  affectedByChange(changedFieldId: number): number[] {
    return Array.from(this.dependentsByField.get(changedFieldId) ?? []);
  }
}

/** Flattens a parsed FormLayout into its columns, for building an EnabledRulesEngine. */
export function collectColumns(layout: readonly { rows: readonly { columns: readonly CustomColUiDto[] }[] }[]): CustomColUiDto[] {
  return layout.flatMap((panel) => panel.rows.flatMap((row) => row.columns));
}
