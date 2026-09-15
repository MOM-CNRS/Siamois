import { describe, expect, it } from "vitest";
import { EnabledRulesEngine, collectColumns } from "../rulesEngine";
import type { CustomColUiDto, EnabledWhenJson, FormLayout } from "../schema";

const SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS =
  "fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectOneFromFieldAnswerCode";

function eqOnConcept(fieldId: number, vocabularyExtId: string, conceptExtId: string): EnabledWhenJson {
  return {
    op: "EQ",
    fieldId,
    values: [{ answerClass: SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS, value: { vocabularyExtId, conceptExtId } }],
  };
}

describe("EnabledRulesEngine — concept EQ/NEQ/IN (the one implemented matcher)", () => {
  it("EQ enables the column only when the depended-on field's concept matches exactly", () => {
    const col: CustomColUiDto = { fieldId: 2, enabledWhen: eqOnConcept(1, "th230", "1234") };
    const engine = new EnabledRulesEngine([col]);

    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "1234" }))).toBe(true);
    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "9999" }))).toBe(false);
    expect(engine.isEnabled(2, () => null)).toBe(false);
  });

  it("NEQ is the exact logical negation of EQ", () => {
    const col: CustomColUiDto = { fieldId: 2, enabledWhen: { ...eqOnConcept(1, "th230", "1234"), op: "NEQ" } };
    const engine = new EnabledRulesEngine([col]);

    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "1234" }))).toBe(false);
    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "9999" }))).toBe(true);
    // NEQ against a null current answer: defaultMatcher/forSelectOneFromFieldCode(null) -> false -> !false -> true
    expect(engine.isEnabled(2, () => null)).toBe(true);
  });

  it("IN is an OR across all values, and false when the current answer is null (no AND/OR combinator beyond that)", () => {
    const enabledWhen: EnabledWhenJson = {
      op: "IN",
      fieldId: 1,
      values: [
        { answerClass: SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS, value: { vocabularyExtId: "th230", conceptExtId: "1" } },
        { answerClass: SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS, value: { vocabularyExtId: "th230", conceptExtId: "2" } },
      ],
    };
    const col: CustomColUiDto = { fieldId: 2, enabledWhen };
    const engine = new EnabledRulesEngine([col]);

    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "1" }))).toBe(true);
    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "2" }))).toBe(true);
    expect(engine.isEnabled(2, () => ({ vocabularyExtId: "th230", conceptExtId: "3" }))).toBe(false);
    expect(engine.isEnabled(2, () => null)).toBe(false);
  });

  it("a column with no enabledWhen is always enabled", () => {
    const engine = new EnabledRulesEngine([{ fieldId: 5 }]);
    expect(engine.isEnabled(5, () => "anything")).toBe(true);
    expect(engine.isEnabled(999, () => null)).toBe(true); // unknown column id: no rule -> enabled
  });
});

describe("EnabledRulesEngine — default-false fallback for unimplemented matcher kinds", () => {
  it("an answerClass other than the one implemented server-side always fails to match (mirrors ValueMatcherFactory.defaultMatcher)", () => {
    const enabledWhen: EnabledWhenJson = {
      op: "EQ",
      fieldId: 1,
      values: [{ answerClass: "fr.siamois.domain.models.form.customfieldanswer.SomeOtherAnswerClass", value: "whatever" }],
    };
    const col: CustomColUiDto = { fieldId: 2, enabledWhen };
    const engine = new EnabledRulesEngine([col]);

    // EQ against an always-false matcher -> disabled, even with a plausible-looking matching value
    expect(engine.isEnabled(2, () => "whatever")).toBe(false);
  });

  it("NEQ against an unimplemented matcher kind is always true (negation of always-false)", () => {
    const enabledWhen: EnabledWhenJson = {
      op: "NEQ",
      fieldId: 1,
      values: [{ answerClass: "fr.siamois.domain.models.form.customfieldanswer.SomeOtherAnswerClass", value: "whatever" }],
    };
    const col: CustomColUiDto = { fieldId: 2, enabledWhen };
    const engine = new EnabledRulesEngine([col]);

    expect(engine.isEnabled(2, () => "whatever")).toBe(true);
  });
});

describe("EnabledRulesEngine — fail-closed on evaluation error", () => {
  it("disables the column if the lookup throws", () => {
    const col: CustomColUiDto = { fieldId: 2, enabledWhen: eqOnConcept(1, "th230", "1234") };
    const engine = new EnabledRulesEngine([col]);

    expect(
      engine.isEnabled(2, () => {
        throw new Error("boom");
      }),
    ).toBe(false);
  });
});

describe("EnabledRulesEngine — reactive dependency index", () => {
  it("affectedByChange reports every column depending on the changed field, and only those", () => {
    const cols: CustomColUiDto[] = [
      { fieldId: 10, enabledWhen: eqOnConcept(1, "th230", "1") },
      { fieldId: 11, enabledWhen: eqOnConcept(1, "th230", "2") },
      { fieldId: 12, enabledWhen: eqOnConcept(2, "th230", "1") },
      { fieldId: 13 }, // no rule at all
    ];
    const engine = new EnabledRulesEngine(cols);

    expect(new Set(engine.affectedByChange(1))).toEqual(new Set([10, 11]));
    expect(new Set(engine.affectedByChange(2))).toEqual(new Set([12]));
    expect(engine.affectedByChange(999)).toEqual([]);
  });
});

describe("collectColumns", () => {
  it("flattens panels/rows into a single column list", () => {
    const layout: FormLayout = [
      { rows: [{ columns: [{ fieldId: 1 }, { fieldId: 2 }] }, { columns: [{ fieldId: 3 }] }] },
      { rows: [{ columns: [{ fieldId: 4 }] }] },
    ];
    expect(collectColumns(layout).map((c) => c.fieldId)).toEqual([1, 2, 3, 4]);
  });
});
