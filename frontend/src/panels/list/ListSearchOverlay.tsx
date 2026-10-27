import {type KeyboardEvent, memo, useEffect, useMemo, useRef, useState} from "react";
import {InputText} from "primereact/inputtext";
import {useQuery} from "@tanstack/react-query";
import {describeFilterValue} from "../../components/table/ColumnFilter";
import type {FilterSpec} from "../../components/table/FilterChipBar";
import type {FilterOption} from "../../fields/optionSources";
import type {EntityTypeConfig, FilterValue, ListParams, PagedResult} from "../../entities/shared/types";
import {t} from "../../i18n";
import {formatFilter, keywordCompletion, parseQuery, type SearchKeyword, tailKeyword} from "../../search/queryParser";
import {
    anchorOf,
    Highlighted,
    KeywordButtons,
    nextActive,
    type SearchAnchor,
    SearchOverlay,
    SearchRow,
    useDebounced,
} from "../../search/SearchOverlay";
import type {RowRecord} from "./useEntityListData";
import {useToastOnChange} from "../../notify/NotifyProvider";

export const PREVIEW_DEBOUNCE_MS = 250;
const PREVIEW_LIMIT = 8;
const SUGGESTION_LIMIT = 8;

type ListFilterOps = Record<string, FilterValue>;
type ListQueryParams = Omit<ListParams, "offset" | "limit" | "fields">;

export interface ListSearchOverlayProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  // The list's own query (search, sort, scope, filters — the tab's fixed ones included): the preview runs it.
  params: ListQueryParams;
  // What the user filtered on (the table's state), without the tab's fixed filters.
  filters: ListFilterOps;
  filterSpecs: FilterSpec[];
  optionsByKey: Record<string, FilterOption[]>;
  // The search the list currently runs.
  value: string;
  // Enter / « Appliquer »: the list runs this search and these filters.
  onApply: (search: string, filters: ListFilterOps, options: Record<string, FilterOption[]>) => void;
  onFilterRemove: (key: string) => void;
  onOpenRow: (id: string | number) => void;
}

// A keyword is typed in lower case without accents or spaces: « Type d'UE » → `type-d-ue`.
function slug(label: string): string {
  return label
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

const OPTION_KINDS = new Set(["concept-one", "concept-many", "in"]);

/** The filters of the list a user can type as `keyword:value`: the plain text ones, and those with options to suggest. */
export function keywordsOf(specs: readonly FilterSpec[]): SearchKeyword[] {
  const seen = new Set<string>();
  return specs.flatMap((spec) => {
    const name = slug(spec.label);
    if (!name || seen.has(name)) return [];
    if (spec.kind !== "contains" && !(OPTION_KINDS.has(spec.kind) && spec.loadOptions)) return [];
    seen.add(name);
    return [{ key: spec.key, names: [name] }];
  });
}

function textOf(node: unknown): string {
  return typeof node === "string" || typeof node === "number" ? String(node) : "";
}

type Row = { kind: "item"; id: string | number } | { kind: "suggestion"; option: FilterOption } | { kind: "apply" };

/**
 * The list's search bar: the field the toolbar shows is only the way in, the search happens in an
 * overlay modelled on the header's. It runs the list's own query for a preview and reads the list's
 * filters; nothing changes in the table until Enter or « Appliquer ».
 */
export const ListSearchOverlayBox = memo(function ListSearchOverlayBox(props: Readonly<ListSearchOverlayProps>) {
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState<SearchAnchor>();
  const field = useRef<HTMLSpanElement>(null);

  function openOverlay() {
    setAnchor(anchorOf(field.current));
    setOpen(true);
  }

  return (
    <>
      <span className="p-input-icon-left sia-list-search" ref={field}>
        {/* The icon is part of the clickable field: it sits over the input, so it opens the search too. */}
        <i className="bi bi-search sia-list-search-icon" onClick={openOverlay} />
        <InputText
          readOnly
          placeholder={t("list.search")}
          value={props.value}
          aria-haspopup="dialog"
          onClick={openOverlay}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " " || event.key === "ArrowDown") {
              event.preventDefault();
              openOverlay();
            }
          }}
        />
        {props.value !== "" && (
          <i
            className="bi bi-x-lg sia-search-clear"
            role="button"
            tabIndex={0}
            aria-label={t("search.clear")}
            onClick={() => props.onApply("", props.filters, {})}
            onKeyDown={(event) => event.key === "Enter" && props.onApply("", props.filters, {})}
          />
        )}
      </span>
      {open && <ListSearchPanel {...props} anchor={anchor} onClose={() => setOpen(false)} />}
    </>
  );
});

function ListSearchPanel({
  entityType,
  config,
  params,
  filters,
  filterSpecs,
  optionsByKey,
  value,
  onApply,
  onFilterRemove,
  onOpenRow,
  anchor,
  onClose,
}: Readonly<ListSearchOverlayProps & { anchor?: SearchAnchor; onClose: () => void }>) {
  const [input, setInput] = useState(value);
  const [active, setActive] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const keywords = useMemo(() => keywordsOf(filterSpecs), [filterSpecs]);
  const specByKey = useMemo(() => new Map(filterSpecs.map((s) => [s.key, s])), [filterSpecs]);
  // The options picked from the suggestions of a keyword: `keyword:label` becomes their id on apply.
  const [picked, setPicked] = useState<Record<string, FilterOption>>({});

  const tail = useMemo(() => tailKeyword(input, keywords), [input, keywords]);
  const parsed = useMemo(() => parseQuery(input, keywords), [input, keywords]);

  // What the typed keywords mean as filters of the list, with the options they were picked from.
  const typed = useMemo(() => {
    const values: ListFilterOps = {};
    const options: Record<string, FilterOption[]> = {};
    for (const f of parsed.filters) {
      const spec = specByKey.get(f.key);
      if (!spec) continue;
      if (spec.kind === "contains") {
        values[f.key] = { op: "contains", v: f.value };
      } else {
        const option = picked[`${f.key}:${f.value}`];
        if (!option) continue;
        values[f.key] = { op: "in", v: [option.id] };
        options[f.key] = [option];
      }
    }
    return { values, options };
  }, [parsed, specByKey, picked]);

  const hasQuery = parsed.text.length > 0 || Object.keys(typed.values).length > 0;
  const wanted = useMemo(
    () => ({ search: parsed.text || undefined, filters: { ...params.filters, ...typed.values } }),
    [parsed.text, params.filters, typed.values],
  );
  const debounced = useDebounced(wanted, PREVIEW_DEBOUNCE_MS);
  const preview = useQuery({
    queryKey: ["list-search-preview", entityType, params.organizationId, params.scope, debounced],
    queryFn: () =>
      config.api.list({
        ...params,
        search: debounced.search,
        filters: Object.keys(debounced.filters).length > 0 ? debounced.filters : undefined,
        offset: 0,
        limit: PREVIEW_LIMIT,
      }) as Promise<PagedResult<RowRecord>>,
    enabled: hasQuery && !tail?.partial,
    staleTime: 15_000,
    retry: false,
  });
  useToastOnChange(preview.isError ? t("search.error") : null);
  const previewRows = preview.data?.data;
  const rowsData = useMemo(() => (hasQuery ? (previewRows ?? []) : []), [hasQuery, previewRows]);
  const total = hasQuery ? (preview.data?.totalCount ?? 0) : 0;

  // The options to complete a keyword with: the ones of the filter being typed.
  const tailSpec = tail ? specByKey.get(tail.key) : undefined;
  const partial = useDebounced(tail?.partial ?? "", PREVIEW_DEBOUNCE_MS);
  const suggestionQuery = useQuery({
    queryKey: ["list-search-options", entityType, params.organizationId, tail?.key, partial],
    queryFn: () => tailSpec!.loadOptions!(partial),
    enabled: tailSpec?.loadOptions != null && partial.length > 0,
    staleTime: 15_000,
    retry: false,
  });
  const suggestions = useMemo(
    () => (tail && tailSpec?.loadOptions && tail.partial.length > 0 ? (suggestionQuery.data ?? []).slice(0, SUGGESTION_LIMIT) : []),
    [tail, tailSpec, suggestionQuery.data],
  );

  const identifier = config.list.columns.find((c) => c.identifier);
  const secondary = config.list.columns.find((c) => !c.identifier && !c.unscopedOnly);
  const labelOf = (row: RowRecord) => (identifier ? textOf(identifier.render(row as never)) : "") || String(row.id ?? "");
  const subtitleOf = (row: RowRecord) => (secondary ? textOf(secondary.render(row as never)) : "");

  const rows = useMemo<Row[]>(
    () =>
      suggestions.length > 0
        ? suggestions.map<Row>((option) => ({ kind: "suggestion", option }))
        : [
            ...rowsData.map<Row>((r) => ({ kind: "item", id: r.id as string | number })),
            ...(hasQuery && total > 0 ? [{ kind: "apply" } as Row] : []),
          ],
    [suggestions, rowsData, hasQuery, total],
  );
  useEffect(() => setActive(-1), [rows]);

  function apply() {
    const nextFilters = { ...filters, ...typed.values };
    onApply(parsed.text, nextFilters, typed.options);
    onClose();
  }

  // Emptying the field also empties the search the table runs (#669): it keeps its filters, drops its text.
  function clear() {
    setInput("");
    setPicked({});
    inputRef.current?.focus();
    if (value !== "") onApply("", filters, {});
  }

  function run(row: Row) {
    if (row.kind === "suggestion") {
      const at = tail?.start ?? input.length;
      const name = keywords.find((k) => k.key === tail?.key)?.names[0] ?? tail?.key ?? "";
      setPicked((p) => ({ ...p, [`${tail?.key}:${row.option.label}`]: row.option }));
      setInput(`${input.slice(0, at)}${formatFilter({ key: name, value: row.option.label })} `);
      inputRef.current?.focus();
    } else if (row.kind === "item") {
      onClose();
      onOpenRow(row.id);
    } else {
      apply();
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      onClose();
    } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      if (rows.length === 0) return;
      event.preventDefault();
      setActive((current) => nextActive(current, event.key as "ArrowDown" | "ArrowUp", rows.length));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const row = rows[active] ?? (suggestions.length > 0 ? rows[0] : undefined);
      if (row) run(row);
      else apply();
    }
  }

  function insertKeyword(key: string) {
    const name = keywords.find((k) => k.key === key)?.names[0] ?? key;
    const completion = keywordCompletion(input, keywords);
    const base = completion ? input.replace(/\S+$/, "") : input;
    setInput(`${base}${base === "" || base.endsWith(" ") ? "" : " "}${name}:`);
    inputRef.current?.focus();
  }

  const chips = filterSpecs.filter((spec) => filters[spec.key] != null);
  let index = -1;
  const next = () => ++index;

  return (
    <SearchOverlay
      anchor={anchor}
      keywords={keywords}
      onClear={clear}
      ariaLabel={t("list.search")}
      placeholder={t("list.search")}
      input={input}
      active={active}
      inputRef={inputRef}
      onInput={setInput}
      onKeyDown={onKeyDown}
      onClose={onClose}
      beforeDropdown={
        chips.length > 0 && (
          <div className="entity-list-panel-filter-chips sia-search-chips">
            {chips.map((spec) => (
              <span key={spec.key} className="filter-chip">
                <span className="filter-chip-body">
                  <span className="filter-chip-label">{spec.label}</span>
                  <span className="filter-chip-value">{describeFilterValue(filters[spec.key], optionsByKey[spec.key])}</span>
                </span>
                <button
                  type="button"
                  className="filter-chip-remove"
                  aria-label={t("filter.remove", { label: spec.label })}
                  onClick={() => onFilterRemove(spec.key)}
                >
                  <i className="bi bi-x" />
                </button>
              </span>
            ))}
          </div>
        )
      }
    >
      {suggestions.map((option) => (
        <SearchRow key={option.id} index={next()} active={active} onSelect={() => run({ kind: "suggestion", option })}>
          <span className="sia-search-row-label">
            <Highlighted text={option.label} term={tail?.partial ?? ""} />
          </span>
          <span className="sia-search-autocomplete">{t("search.autocomplete")}</span>
        </SearchRow>
      ))}

      {suggestions.length === 0 && preview.isFetching && hasQuery && <div className="sia-search-status">{t("search.loading")}</div>}
      {suggestions.length === 0 && !preview.isFetching && !preview.isError && hasQuery && debounced.search === wanted.search && rowsData.length === 0 && (
        <div className="sia-search-status">{t("search.empty", { q: parsed.text || input })}</div>
      )}

      {suggestions.length === 0 &&
        rowsData.map((row) => (
          <SearchRow key={String(row.id)} index={next()} active={active} onSelect={() => run({ kind: "item", id: row.id as string | number })}>
            <span className="sia-search-row-label">
              <Highlighted text={labelOf(row)} term={parsed.text} />
            </span>
            {subtitleOf(row) && <span className="sia-search-row-sub">{subtitleOf(row)}</span>}
          </SearchRow>
        ))}

      {suggestions.length === 0 && hasQuery && total > 0 && (
        <SearchRow index={next()} active={active} className="sia-search-all" onSelect={apply}>
          {t("list.search.apply", { n: total })}
          <i className="bi bi-arrow-return-left" />
        </SearchRow>
      )}

      {keywords.length > 0 && (
        <KeywordButtons
          keywords={keywords}
          title={(keyword) => t("list.search.keyword", { label: specByKey.get(keyword.key)?.label ?? keyword.key })}
          onInsert={insertKeyword}
        />
      )}
    </SearchOverlay>
  );
}
