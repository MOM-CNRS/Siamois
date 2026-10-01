import { memo, useEffect, useRef, useState } from "react";
import { InputText } from "primereact/inputtext";
import { t } from "../../i18n";

// tableToolbar.xhtml's globalFilter is debounced 500ms server-side; 300ms here errs toward
// responsiveness since the request itself is already async. Either way: not one query per
// keystroke.
export const SEARCH_DEBOUNCE_MS = 300;

export interface ListSearchBoxProps {
  // The search the list currently runs (its committed value).
  value: string;
  onSearch: (search: string) => void;
}

/**
 * The list's search input. It owns what is being typed and only reports it once typing pauses, so a
 * keystroke re-renders this box alone — never the table under it.
 */
export const ListSearchBox = memo(function ListSearchBox({ value, onSearch }: ListSearchBoxProps) {
  const [input, setInput] = useState(value);

  // What this box last reported: a `value` that differs from it came from outside (the list's state
  // was reset) and replaces what is typed; one that equals it is just our own search echoed back,
  // which must not overwrite characters typed since.
  const reported = useRef(value);
  useEffect(() => {
    if (value !== reported.current) {
      reported.current = value;
      setInput(value);
    }
  }, [value]);

  useEffect(() => {
    if (input === reported.current) return;
    const handle = setTimeout(() => {
      reported.current = input;
      onSearch(input);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [input, onSearch]);

  return (
    <span className="p-input-icon-left">
      <i className="bi bi-search" />
      <InputText placeholder={t("list.search")} value={input} onChange={(e) => setInput(e.target.value)} />
    </span>
  );
});
