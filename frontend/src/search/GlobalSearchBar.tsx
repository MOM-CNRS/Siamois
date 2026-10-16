import { useEffect, useMemo, useRef, useState, useSyncExternalStore, type KeyboardEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { getEntityType } from "../entities/registry";
import { getContext, getNavigation, subscribeContext } from "../navigation/externalBus";
import { t } from "../i18n";
import { formatFilter, keywordCompletion, parseQuery, SEARCH_KEYWORDS } from "./queryParser";
import { fetchGlobalSearch, type SearchItem, type SearchSection } from "./searchApi";

export const SEARCH_DEBOUNCE_MS = 250;
const SECTION_LIMIT = 5;

// What Enter / a click does on one row of the dropdown.
type Row =
  | { kind: "item"; type: string; item: SearchItem }
  | { kind: "all"; type: string; total: number };

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const handle = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(handle);
  }, [value, delay]);
  return debounced;
}

/** The project every item of every section belongs to, when there is exactly one. */
function commonProjectId(sections: SearchSection[]): string | undefined {
  const ids = new Set(sections.flatMap((s) => s.items.map((i) => i.projectId)).filter((id) => id != null));
  return ids.size === 1 ? String([...ids][0]) : undefined;
}

// The "motherlode" Snake game of the JSF page (SnakeGameBean's dialog), kept from the former search box.
function openSnakeEasterEgg(typed: string): boolean {
  if (typed.trim().toLowerCase() !== "motherlode") return false;
  const pf = (window as unknown as { PF?: (widget: string) => { show?: () => void } | undefined }).PF;
  const dialog = pf?.("snakeGameDiag");
  dialog?.show?.();
  return dialog != null;
}

function Highlighted({ text, term }: Readonly<{ text: string; term: string }>) {
  const at = term ? text.toLowerCase().indexOf(term.toLowerCase()) : -1;
  if (at < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, at)}
      <mark className="sia-search-mark">{text.slice(at, at + term.length)}</mark>
      {text.slice(at + term.length)}
    </>
  );
}

/**
 * The header search bar: results grouped by entity type, `keyword:value` filters, and the current
 * project as an implicit filter that can be taken off. It lives in its own React root (the JSF
 * topbar), and drives the main panel's navigation through navigation/externalBus.
 */
export function GlobalSearchBar({ organizationId: mountOrganizationId }: Readonly<{ organizationId?: number }>) {
  const context = useSyncExternalStore(subscribeContext, getContext);
  const organizationId = context.organizationId ?? mountOrganizationId;

  const [input, setInput] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  // The current project narrows the search until the user takes it off; a new project puts it back.
  const [scopeOff, setScopeOff] = useState(false);
  useEffect(() => setScopeOff(false), [context.projectId]);
  const root = useRef<HTMLDivElement>(null);

  const parsed = useMemo(() => parseQuery(input), [input]);
  const explicitProject = parsed.filters.some((f) => f.key === "projet");
  const implicitProjectId = !scopeOff && !explicitProject ? context.projectId : undefined;
  const hasQuery = parsed.text.length > 0 || parsed.filters.length > 0;

  const wanted = useMemo(
    () => ({ text: parsed.text, filters: parsed.filters, projectId: implicitProjectId }),
    [parsed, implicitProjectId],
  );
  const debounced = useDebounced(wanted, SEARCH_DEBOUNCE_MS);
  const debouncedHasQuery = debounced.text.length > 0 || debounced.filters.length > 0;
  const search = useQuery({
    queryKey: ["global-search", organizationId, debounced.text, debounced.filters, debounced.projectId],
    queryFn: ({ signal }) =>
      fetchGlobalSearch(
        { organizationId: organizationId!, q: debounced.text, projectId: debounced.projectId, filters: debounced.filters, limit: SECTION_LIMIT },
        signal,
      ),
    enabled: open && organizationId != null && debouncedHasQuery,
    staleTime: 15_000,
    retry: false,
  });
  const sections = useMemo(() => (hasQuery ? (search.data ?? []) : []), [hasQuery, search.data]);

  const rows = useMemo<Row[]>(
    () =>
      sections.flatMap<Row>((section) => [
        ...section.items.map<Row>((item) => ({ kind: "item", type: section.type, item })),
        { kind: "all", type: section.type, total: section.total },
      ]),
    [sections],
  );
  useEffect(() => setActive(-1), [rows]);

  // A click anywhere else closes the dropdown.
  useEffect(() => {
    function onPointerDown(event: PointerEvent) {
      if (root.current && !root.current.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, []);

  function run(row: Row) {
    const navigation = getNavigation();
    setOpen(false);
    if (!navigation) return;
    if (row.kind === "item") navigation.navigate(row.type, row.item.id);
    else
      navigation.openList(row.type, {
        search: parsed.text || undefined,
        projectId: implicitProjectId ?? (explicitProject ? commonProjectId(sections) : undefined),
      });
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      setOpen(false);
    } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      if (rows.length === 0) return;
      event.preventDefault();
      setOpen(true);
      const step = event.key === "ArrowDown" ? 1 : -1;
      // -1 (nothing highlighted: Enter then means "see all") is part of the cycle.
      setActive((current) => ((current + 1 + step + rows.length + 1) % (rows.length + 1)) - 1);
    } else if (event.key === "Enter") {
      const row = rows[active] ?? rows.find((r) => r.kind === "all");
      if (row) {
        event.preventDefault();
        run(row);
      }
    }
  }

  const completion = keywordCompletion(input);
  function insertKeyword(name: string) {
    setInput((current) => {
      // A half-typed keyword (`proj`) is completed; otherwise the keyword starts a new word.
      const base = completion ? current.replace(/\S+$/, "") : current;
      const separator = base === "" || base.endsWith(" ") ? "" : " ";
      return `${base}${separator}${name}:`;
    });
    setOpen(true);
  }

  const showHelp = !hasQuery;
  let activeIndex = -1;
  const rowIndex = () => ++activeIndex;
  const projectLabel = context.projectLabel
    ? t("search.projectScope", { label: context.projectLabel })
    : t("search.projectScopeCurrent");

  return (
    <div className="sia-search" ref={root}>
      <span className="p-input-icon-left p-input-icon-right sia-search-field">
        <i className="bi bi-search" />
        <input
          className="p-inputtext sia-search-input"
          type="search"
          role="combobox"
          aria-expanded={open}
          aria-controls="sia-search-listbox"
          aria-label={t("search.ariaLabel")}
          aria-activedescendant={active >= 0 ? `sia-search-row-${active}` : undefined}
          placeholder={t("search.placeholder")}
          value={input}
          autoComplete="off"
          onChange={(event) => {
            if (openSnakeEasterEgg(event.target.value)) {
              setInput("");
              return;
            }
            setInput(event.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {input && (
          <i
            className="bi bi-x-lg sia-search-clear"
            role="button"
            tabIndex={0}
            aria-label={t("search.clear")}
            onClick={() => setInput("")}
            onKeyDown={(event) => event.key === "Enter" && setInput("")}
          />
        )}
      </span>

      {open && (
        <div className="sia-search-dropdown" id="sia-search-listbox" role="listbox">
          {context.projectId && !explicitProject && (
            <div className="sia-search-scope">
              <span className={`sia-search-chip${scopeOff ? " is-off" : ""}`}>
                <i className="bi bi-funnel" />
                {projectLabel}
                <button
                  type="button"
                  className="sia-search-chip-toggle"
                  aria-label={scopeOff ? projectLabel : t("search.projectScopeRemove")}
                  title={scopeOff ? projectLabel : t("search.projectScopeRemove")}
                  onClick={() => setScopeOff((off) => !off)}
                >
                  <i className={`bi ${scopeOff ? "bi-plus-lg" : "bi-x-lg"}`} />
                </button>
              </span>
            </div>
          )}

          {search.isFetching && hasQuery && <div className="sia-search-status">{t("search.loading")}</div>}
          {search.isError && <div className="sia-search-status is-error">{t("search.error")}</div>}
          {!search.isFetching && !search.isError && hasQuery && debouncedHasQuery && sections.length === 0 && (
            <div className="sia-search-status">{t("search.empty", { q: parsed.text || input })}</div>
          )}

          {sections.map((section) => {
            const config = getEntityType(section.type);
            return (
              <div className="sia-search-section" key={section.type}>
                <div className="sia-search-section-title">
                  <i className={config?.icon} />
                  {config?.labels.plural ?? section.type}
                  <span className="sia-search-count">{section.total}</span>
                </div>
                {section.items.map((item) => {
                  const index = rowIndex();
                  return (
                    <div
                      key={item.id}
                      id={`sia-search-row-${index}`}
                      role="option"
                      aria-selected={index === active}
                      className={`sia-search-row${index === active ? " is-active" : ""}`}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => run({ kind: "item", type: section.type, item })}
                    >
                      <span className="sia-search-row-label">
                        <Highlighted text={item.label} term={parsed.text} />
                      </span>
                      {item.subtitle && <span className="sia-search-row-sub">{item.subtitle}</span>}
                    </div>
                  );
                })}
                {(() => {
                  const index = rowIndex();
                  return (
                    <div
                      id={`sia-search-row-${index}`}
                      role="option"
                      aria-selected={index === active}
                      className={`sia-search-row sia-search-all${index === active ? " is-active" : ""}`}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => run({ kind: "all", type: section.type, total: section.total })}
                    >
                      {t("search.viewAll", { n: section.total, label: (config?.labels.plural ?? section.type).toLowerCase() })}
                      <i className="bi bi-arrow-return-left" />
                    </div>
                  );
                })()}
              </div>
            );
          })}

          {showHelp && <div className="sia-search-status">{t("search.hint")}</div>}
          <div className="sia-search-keywords">
            <span className="sia-search-keywords-title">{t("search.keywords")}</span>
            {SEARCH_KEYWORDS.map((keyword) => (
              <button
                type="button"
                key={keyword.key}
                className="sia-search-keyword"
                title={t("search.keyword.projet")}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => insertKeyword(keyword.key)}
              >
                {formatFilter({ key: keyword.key, value: "" })}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
