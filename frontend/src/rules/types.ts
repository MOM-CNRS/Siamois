// The conditional field rules language — mirrors fr.siamois.domain.models.form.rules.FieldRulesJson
// (the single definition of the wire format). A column of a form layout may carry `rules`; this
// module evaluates them. Deliberately free of any React / PrimeReact / app import so the mobile app
// can embed it offline as is.

export type ConditionOp =
  | "EQ"
  | "NEQ"
  | "IN"
  | "NOT_IN"
  | "EMPTY"
  | "NOT_EMPTY"
  | "GT"
  | "GTE"
  | "LT"
  | "LTE";

/** A concept, by its thesaurus ids; `conceptId` is resolved by the server (absent = unknown here). */
export interface ConceptValueSpec {
  vocabularyExtId: string;
  conceptExtId: string;
  conceptId?: string;
}

/** A referenced entity (person, unit, place…), by its API id. */
export interface RefValueSpec {
  id: string;
}

/** Text, number, boolean, or an ISO-8601 date string. */
export type LiteralValueSpec = string | number | boolean;

export type FieldValueSpec = ConceptValueSpec | RefValueSpec | LiteralValueSpec;

export interface LeafCondition {
  fieldId: number | string;
  op: ConditionOp;
  values?: FieldValueSpec[];
}

export type Condition =
  | { all: Condition[] }
  | { any: Condition[] }
  | { not: Condition }
  | LeafCondition;

export type OptionsFilter =
  | { kind: "RELATED_CONCEPTS"; fieldId: number | string }
  | { kind: "REF_MATCH"; fieldId: number | string; candidateFieldId: number | string };

export interface FieldConstraint {
  op: "GT" | "GTE" | "LT" | "LTE";
  fieldId: number | string;
}

export interface FieldRules {
  enabledWhen?: Condition;
  requiredWhen?: Condition;
  options?: OptionsFilter;
  constraints?: FieldConstraint[];
}

/** What the evaluator needs from a layout column. */
export interface RuledColumn {
  fieldId?: number | string | null;
  isRequired?: boolean;
  rules?: FieldRules | null;
}

export type IncoherenceReason =
  /** The field holds a value although its enabledWhen is false. */
  | { kind: "DISABLED_WITH_VALUE" }
  /** The value is outside the list the options filter currently allows. */
  | { kind: "OUT_OF_OPTIONS" }
  /** An ordering constraint with another field is violated (either side declared it). */
  | { kind: "CONSTRAINT"; op: FieldConstraint["op"]; otherFieldId: string };

/** What the options source of a filtered field needs to query (or filter offline). */
export type OptionsContext =
  | { kind: "RELATED_CONCEPTS"; parentFieldId: string; relatedTo: string | null }
  | { kind: "REF_MATCH"; parentFieldId: string; candidateFieldId: string; value: string | null };

/** A bound derived from a constraint: `value` is comparable (ms for a date), `raw` as read. */
export interface Bound {
  value: number;
  raw: unknown;
  exclusive: boolean;
  fieldId: string;
}

export interface FieldState {
  enabled: boolean;
  required: boolean;
  incoherent: IncoherenceReason[];
  optionsContext?: OptionsContext;
  bounds?: { min?: Bound; max?: Bound };
}
