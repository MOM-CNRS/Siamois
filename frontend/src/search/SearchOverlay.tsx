import { createPortal } from "react-dom";
import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode, type RefObject } from "react";
import { t } from "../i18n";
import { formatFilter, highlightSegments, type SearchKeyword } from "./queryParser";

const MIN_PANEL_WIDTH = 520;

/** The value, once it has stopped changing for `delay` ms. */
export function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const handle = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(handle);
  }, [value, delay]);
  return debounced;
}

export function Highlighted({ text, term }: Readonly<{ text: string; term: string }>) {
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

export interface SearchAnchor {
  left: number;
  top: number;
  width: number;
}

/** Where the overlay starts: on its trigger's box, but never running off the right edge of the window. */
export function anchorOf(trigger: HTMLElement | null): SearchAnchor | undefined {
  const box = trigger?.getBoundingClientRect();
  if (!box) return undefined;
  const width = Math.min(Math.max(box.width, MIN_PANEL_WIDTH), window.innerWidth - 16);
  return { left: Math.max(8, Math.min(box.left, window.innerWidth - width - 8)), top: box.top, width };
}

/** One selectable row of the dropdown (`sia-search-row-<index>` is what aria-activedescendant points to). */
export function SearchRow({
  index,
  active,
  className,
  onSelect,
  children,
}: Readonly<{ index: number; active: number; className?: string; onSelect: () => void; children: ReactNode }>) {
  return (
    <div
      id={`sia-search-row-${index}`}
      role="option"
      aria-selected={index === active}
      className={`sia-search-row${className ? ` ${className}` : ""}${index === active ? " is-active" : ""}`}
      onMouseDown={(event) => event.preventDefault()}
      onClick={onSelect}
    >
      {children}
    </div>
  );
}

// Keyword buttons shown at the foot of the dropdown; the others are behind « +N ».
export const MAX_KEYWORD_BUTTONS = 4;

/** The keyword buttons at the foot of the dropdown: the first ones, then « +N » that shows the rest. */
export function KeywordButtons({
  keywords,
  title,
  onInsert,
}: Readonly<{ keywords: readonly SearchKeyword[]; title: (keyword: SearchKeyword) => string; onInsert: (key: string) => void }>) {
  const [all, setAll] = useState(false);
  const shown = all ? keywords : keywords.slice(0, MAX_KEYWORD_BUTTONS);
  return (
    <div className="sia-search-keywords">
      {shown.map((keyword) => (
        <button
          type="button"
          key={keyword.key}
          className="sia-search-keyword"
          title={title(keyword)}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => onInsert(keyword.key)}
        >
          {formatFilter({ key: keyword.names[0] ?? keyword.key, value: "" })}
        </button>
      ))}
      {keywords.length > shown.length && (
        <button
          type="button"
          className="sia-search-keyword sia-search-keyword-more"
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => setAll(true)}
        >
          +{keywords.length - shown.length}
        </button>
      )}
    </div>
  );
}

/** Next highlighted row for an arrow key; -1 (nothing highlighted) is part of the cycle. */
export function nextActive(current: number, key: "ArrowDown" | "ArrowUp", count: number): number {
  const step = key === "ArrowDown" ? 1 : -1;
  return ((current + 1 + step + count + 1) % (count + 1)) - 1;
}

export interface SearchOverlayProps {
  anchor?: SearchAnchor;
  ariaLabel: string;
  placeholder: string;
  input: string;
  active: number;
  inputRef: RefObject<HTMLInputElement>;
  onInput: (value: string) => void;
  onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void;
  onClose: () => void;
  // The clear button: defaults to emptying the input; the list also resets the search it runs.
  onClear?: () => void;
  // The keywords whose values the field draws in colour (the header search's `projet`, a list's own columns).
  keywords?: readonly SearchKeyword[];
  // Content between the field and the dropdown (the list's filter chips).
  beforeDropdown?: ReactNode;
  children: ReactNode;
}

/**
 * The frame both search overlays share: a portal over the page, the field (combobox) that takes the
 * typing as soon as it opens, and the dropdown the caller fills with its rows.
 */
export function SearchOverlay({
  anchor,
  ariaLabel,
  placeholder,
  input,
  active,
  inputRef,
  onInput,
  onKeyDown,
  onClose,
  keywords,
  onClear,
  beforeDropdown,
  children,
}: Readonly<SearchOverlayProps>) {
  const focused = useRef(false);
  const mirrorRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!focused.current) {
      focused.current = true;
      inputRef.current?.focus();
    }
  }, [inputRef]);

  return createPortal(
    <div className="sia-search-backdrop" onMouseDown={onClose}>
      <div
        className="sia-search-panel"
        role="dialog"
        aria-label={ariaLabel}
        style={anchor && { left: anchor.left, top: anchor.top, width: anchor.width }}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <span className="sia-search-field">
          <i className="bi bi-search" />
          {/* The typed text drawn under the (transparent-text) input, so keyword values can be coloured. */}
          <div className="sia-search-mirror" aria-hidden="true" ref={mirrorRef}>
            {highlightSegments(input, keywords).map((segment, index) =>
              segment.value ? (
                <span key={index} className="sia-search-kw-value">
                  {segment.text}
                </span>
              ) : (
                <span key={index}>{segment.text}</span>
              ),
            )}
          </div>
          <input
            className="p-inputtext sia-search-input"
            type="search"
            role="combobox"
            aria-expanded
            aria-controls="sia-search-listbox"
            aria-label={ariaLabel}
            aria-activedescendant={active >= 0 ? `sia-search-row-${active}` : undefined}
            placeholder={placeholder}
            value={input}
            autoComplete="off"
            onChange={(event) => onInput(event.target.value)}
            ref={inputRef}
            onKeyDown={onKeyDown}
            onScroll={(event) => {
              if (mirrorRef.current) mirrorRef.current.scrollLeft = event.currentTarget.scrollLeft;
            }}
          />
          {input && (
            <i
              className="bi bi-x-lg sia-search-clear"
              role="button"
              tabIndex={0}
              aria-label={t("search.clear")}
              onClick={() => (onClear ?? (() => onInput("")))()}
              onKeyDown={(event) => event.key === "Enter" && (onClear ?? (() => onInput("")))()}
            />
          )}
        </span>
        {beforeDropdown}
        <div className="sia-search-dropdown" id="sia-search-listbox" role="listbox">
          {children}
        </div>
      </div>
    </div>,
    document.body,
  );
}
