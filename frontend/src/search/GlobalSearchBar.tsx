import {type KeyboardEvent, useEffect, useMemo, useRef, useState, useSyncExternalStore} from "react";
import {useQuery} from "@tanstack/react-query";
import {ProjectName} from "../components/ProjectName";
import {getEntityType} from "../entities/registry";
import {listProjects} from "../entities/project/api";
import {queryKeys} from "../api/queryKeys";
import {getContext, getNavigation, subscribeContext} from "../navigation/externalBus";
import {t} from "../i18n";
import {formatFilter, keywordCompletion, parseQuery, SEARCH_KEYWORDS, tailKeyword} from "./queryParser";
import {
    anchorOf,
    Highlighted,
    KeywordButtons,
    nextActive,
    type SearchAnchor,
    SearchOverlay,
    SearchRow,
    useDebounced,
} from "./SearchOverlay";
import {fetchGlobalSearch, type SearchItem, type SearchSection} from "./searchApi";
import {useToastOnChange} from "../notify/NotifyProvider";

export const SEARCH_DEBOUNCE_MS = 250;
const SECTION_LIMIT = 5;
const SUGGESTION_LIMIT = 8;
const RECENT_LIMIT = 5;

// What Enter / a click does on one row of the dropdown.
type Row =
  | { kind: "item"; type: string; item: SearchItem }
  | { kind: "all"; type: string; total: number }
  // A project offered while a `projet:` keyword is being typed: choosing it completes the keyword.
  | { kind: "suggestion"; item: SearchItem };

/** The theme class that gives a section its entity colour (`--main-color`): the card class without its `sia-` prefix. */
function sectionTheme(type: string): string {
  return getEntityType(type)?.cardClassName?.replace(/^sia-/, "") ?? "";
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
  // The current project is offered as a `projet:` keyword at the start of the query, like one the
  // user typed: they edit or delete it the same way. A new project replaces the one inserted before.
  const autoToken = useRef<string | undefined>(undefined);
  useEffect(() => {
    const next = context.projectId
      ? formatFilter({ key: "projet", value: context.projectLabel ?? context.projectId })
      : undefined;
    const previous = autoToken.current;
    if (next === previous) return;
    autoToken.current = next;
    setInput((current) => {
      const rest = previous && current.startsWith(previous) ? current.slice(previous.length).trimStart() : current;
      return next ? `${next} ${rest}` : rest;
    });
  }, [context.projectId, context.projectLabel]);

  // A `projet:xxx` being typed is completed by suggestions, not yet a filter of the search.
  const tail = useMemo(() => tailKeyword(input), [input]);
  const parsed = useMemo(() => parseQuery(tail ? input.slice(0, tail.start) : input), [input, tail]);
  const projectFilter = parsed.filters.find((f) => f.key === "projet");
  // Searching needs words: a lone `projet:` keyword (the one offered by default) would list a whole project.
  const hasQuery = parsed.text.length > 0;

  const wanted = useMemo(
    () => ({ text: parsed.text, filters: parsed.filters }),
    [parsed],
  );
  const debounced = useDebounced(wanted, SEARCH_DEBOUNCE_MS);
  const debouncedHasQuery = debounced.text.length > 0;
  const search = useQuery({
    queryKey: ["global-search", organizationId, debounced.text, debounced.filters],
    queryFn: ({ signal }) =>
      fetchGlobalSearch(
        { organizationId: organizationId!, q: debounced.text, filters: debounced.filters, limit: SECTION_LIMIT },
        signal,
      ),
    enabled: open && organizationId != null && debouncedHasQuery && !tail,
    staleTime: 15_000,
    retry: false,
  });
  useToastOnChange(search.isError ? t("search.error") : null);
  const sections = useMemo(() => (hasQuery && !tail ? (search.data ?? []) : []), [hasQuery, tail, search.data]);

  // Projects matching what follows `projet:` (identifier or name).
  const partial = useDebounced(tail?.partial ?? "", SEARCH_DEBOUNCE_MS);
  const suggestionQuery = useQuery({
    queryKey: ["global-search-projects", organizationId, partial],
    queryFn: ({ signal }) =>
      fetchGlobalSearch({ organizationId: organizationId!, q: partial, filters: [], types: ["project"], limit: SUGGESTION_LIMIT }, signal),
    enabled: open && organizationId != null && tail != null && partial.length > 0,
    staleTime: 15_000,
    retry: false,
  });
  const suggestions = useMemo(
    () => (tail && tail.partial.length > 0 ? (suggestionQuery.data?.[0]?.items ?? []) : []),
    [tail, suggestionQuery.data],
  );

  // With nothing typed, the projects of the home page (same query, same cache) are offered instead.
  const showRecent = !hasQuery && !tail;
  const recentQuery = useQuery({
    queryKey: queryKeys.projectHomeRecent(organizationId),
    queryFn: () => listProjects({ offset: 0, limit: RECENT_LIMIT, sort: "creationTime:desc", organizationId }),
    enabled: open && organizationId != null && showRecent,
    staleTime: 15_000,
  });
  const recent = useMemo<SearchItem[]>(
    () =>
      showRecent
        ? (recentQuery.data?.data ?? []).map((p) => ({ id: Number(p.id), label: p.fullIdentifier, subtitle: p.name }))
        : [],
    [showRecent, recentQuery.data],
  );

  const rows = useMemo<Row[]>(
    () =>
      tail
        ? suggestions.map<Row>((item) => ({ kind: "suggestion", item }))
        : showRecent
          ? recent.map<Row>((item) => ({ kind: "item", type: "project", item }))
          : sections.flatMap<Row>((section) => [
            ...section.items.map<Row>((item) => ({ kind: "item", type: section.type, item })),
            { kind: "all", type: section.type, total: section.total },
          ]),
    [tail, suggestions, showRecent, recent, sections],
  );
  useEffect(() => setActive(-1), [rows]);

  // "/" opens the search from anywhere that is not a text field, like GitHub's.
  useEffect(() => {
    function onKeyDown(event: globalThis.KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const typing = target != null && (target.isContentEditable || /^(input|textarea|select)$/i.test(target.tagName));
      if (event.key === "/" && !typing && !event.metaKey && !event.ctrlKey && !event.altKey) {
        event.preventDefault();
        openOverlay();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  // The overlay starts where the button is and covers it: placed from the button's box when it opens.
  const trigger = useRef<HTMLButtonElement>(null);
  const [anchor, setAnchor] = useState<SearchAnchor>();
  function openOverlay() {
    setAnchor(anchorOf(trigger.current));
    setOpen(true);
  }

  const inputRef = useRef<HTMLInputElement>(null);

  // The project a « Voir tout » list is restricted to: the current one when the keyword names it,
  // otherwise the one every result shares.
  function listProjectId(): string | undefined {
    if (!projectFilter) return undefined;
    const names = [context.projectLabel, context.projectId];
    if (context.projectId && names.includes(projectFilter.value)) return context.projectId;
    return commonProjectId(sections);
  }

  function run(row: Row) {
    if (row.kind === "suggestion") {
      // The typed `projet:xxx` becomes `projet:<identifier>`, and the user goes on with the words.
      setInput((current) => {
        const at = tailKeyword(current)?.start ?? current.length;
        return `${current.slice(0, at)}${formatFilter({ key: "projet", value: row.item.label })} `;
      });
      return;
    }
    const navigation = getNavigation();
    setOpen(false);
    if (!navigation) return;
    if (row.kind === "item") navigation.navigate(row.type, row.item.id);
    else
      navigation.openList(row.type, {
        search: parsed.text || undefined,
        projectId: listProjectId(),
      });
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      setOpen(false);
    } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      if (rows.length === 0) return;
      event.preventDefault();
      setOpen(true);
      // -1 (nothing highlighted: Enter then means "see all") is part of the cycle.
      setActive((current) => nextActive(current, event.key as "ArrowDown" | "ArrowUp", rows.length));
    } else if (event.key === "Enter") {
      const row = rows[active] ?? (tail ? rows[0] : rows.find((r) => r.kind === "all"));
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

  let activeIndex = -1;
  const rowIndex = () => ++activeIndex;

  function onInput(value: string) {
    if (openSnakeEasterEgg(value)) {
      setInput("");
      return;
    }
    setInput(value);
    setOpen(true);
  }

  return (
    <div className="sia-search">
      {/* What the topbar shows is only the way in: the search itself happens in the overlay. */}
      <button type="button" className="sia-search-trigger" aria-haspopup="dialog" aria-label={t("search.ariaLabel")} ref={trigger} onClick={openOverlay}>
        <i className="bi bi-search" />
        <span className="sia-search-trigger-text">{t("search.placeholder")}</span>
      </button>

      {open && (
        <SearchOverlay
          anchor={anchor}
          ariaLabel={t("search.ariaLabel")}
          placeholder={t("search.placeholder")}
          input={input}
          active={active}
          inputRef={inputRef}
          onInput={onInput}
          onKeyDown={onKeyDown}
          onClose={() => setOpen(false)}
        >
          {tail && suggestions.length > 0 && (
            <div className={`sia-search-section ${sectionTheme("project")}`}>
              <div className="sia-search-section-title">
                <i className={getEntityType("project")?.icon} />
                {getEntityType("project")?.labels.plural}
              </div>
              {suggestions.map((item, index) => (
                <SearchRow key={item.id} index={index} active={active} onSelect={() => run({ kind: "suggestion", item })}>
                  <span className="sia-search-row-label">
                    <Highlighted text={item.label} term={tail.partial} />
                  </span>
                  {item.subtitle && (
                    <span className="sia-search-row-sub">
                      <Highlighted text={item.subtitle} term={tail.partial} />
                    </span>
                  )}
                  {/* Tells a completion of the keyword being typed from a search result. */}
                  <span className="sia-search-autocomplete">{t("search.autocomplete")}</span>
                </SearchRow>
              ))}
            </div>
          )}

          {recent.length > 0 && (
            <div className={`sia-search-section ${sectionTheme("project")}`}>
              <div className="sia-search-section-title">
                <i className={getEntityType("project")?.icon} />
                {t("home.recentProjects")}
              </div>
              {recent.map((item, index) => (
                <SearchRow key={item.id} index={index} active={active} onSelect={() => run({ kind: "item", type: "project", item })}>
                  <span className="sia-search-row-label">{item.label}</span>
                  {item.subtitle && <span className="sia-search-row-sub">{item.subtitle}</span>}
                </SearchRow>
              ))}
            </div>
          )}

          {search.isFetching && hasQuery && !tail && <div className="sia-search-status">{t("search.loading")}</div>}
          {!tail && !search.isFetching && !search.isError && hasQuery && debouncedHasQuery && sections.length === 0 && (
            <div className="sia-search-status">{t("search.empty", { q: parsed.text || input })}</div>
          )}

          {sections.map((section) => {
            const config = getEntityType(section.type);
            return (
              <div className={`sia-search-section ${sectionTheme(section.type)}`} key={section.type}>
                <div className="sia-search-section-title">
                  <i className={config?.icon} />
                  {config?.labels.plural ?? section.type}
                </div>
                {section.items.map((item) => (
                  <SearchRow
                    key={item.id}
                    index={rowIndex()}
                    active={active}
                    onSelect={() => run({ kind: "item", type: section.type, item })}
                  >
                    <span className="sia-search-row-label">
                      <Highlighted text={item.label} term={parsed.text} />
                    </span>
                    {/* The subtitle of a fiche is its project's identifier: the project name replaces it. */}
                    {item.subtitle && !item.projectName && <span className="sia-search-row-sub">{item.subtitle}</span>}
                    {/* With a project keyword every result is of that project: the name would repeat it on each row. */}
                    {!projectFilter && item.projectName && item.projectId != null && (
                      <ProjectName project={{ id: item.projectId, name: item.projectName }} />
                    )}
                  </SearchRow>
                ))}
                <SearchRow
                  index={rowIndex()}
                  active={active}
                  className="sia-search-all"
                  onSelect={() => run({ kind: "all", type: section.type, total: section.total })}
                >
                  {t("search.viewAll", { n: section.total, label: (config?.labels.plural ?? section.type).toLowerCase() })}
                  <i className="bi bi-arrow-return-left" />
                </SearchRow>
              </div>
            );
          })}

          <KeywordButtons keywords={SEARCH_KEYWORDS} title={() => t("search.keyword.projet")} onInsert={insertKeyword} />
        </SearchOverlay>
      )}
    </div>
  );
}
