import { useEffect, useRef, useState, type ReactNode } from "react";
import { InputText } from "primereact/inputtext";

export interface PickerOption {
  id: string;
  label: string;
}

interface Props {
  value: PickerOption | null;
  onChange: (value: PickerOption | null) => void;
  // The options matching a search — called with "" as soon as the field is focused, so a click alone lists some.
  load: (query: string) => Promise<PickerOption[]>;
  placeholder: string;
  autoFocus?: boolean;
  // A last line of the list, e.g. "create a new one".
  footer?: ReactNode;
}

// How long typing pauses before the search goes out.
const TYPING_PAUSE_MS = 200;

// A search field whose results unfold below it in the flow of the form — not in a floating panel,
// which an overlay would clip. A click on the field searches, even when it is empty.
export function InlinePicker({ value, onChange, load, placeholder, autoFocus, footer }: Props) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<PickerOption[]>([]);
  const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  // Only the latest search may answer: a slow one must not overwrite a newer list.
  const latest = useRef(0);

  const run = async (q: string) => {
    const ticket = ++latest.current;
    setLoading(true);
    try {
      const found = await load(q);
      if (ticket === latest.current) setOptions(found);
    } catch {
      if (ticket === latest.current) setOptions([]);
    } finally {
      if (ticket === latest.current) setLoading(false);
    }
  };

  const search = (q: string) => {
    clearTimeout(timer.current);
    // The first search of an opening goes at once; typing waits for a pause.
    if (q === "") void run(q);
    else timer.current = setTimeout(() => void run(q), TYPING_PAUSE_MS);
  };

  useEffect(() => () => clearTimeout(timer.current), []);

  const show = () => {
    setOpen(true);
    search(query);
  };
  const pick = (option: PickerOption | null) => {
    onChange(option);
    setQuery("");
    if (option) setOpen(false);
    else search("");
  };

  return (
    <div className="sia-inline-picker">
      <InputText
        value={open ? query : (value?.label ?? "")}
        placeholder={value ? undefined : placeholder}
        autoFocus={autoFocus}
        onFocus={show}
        onClick={show}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
          search(e.target.value);
        }}
      />
      {open && (
        <ul className="sia-inline-picker-list">
          {value && (
            <li>
              <button type="button" className="sia-inline-picker-item" onMouseDown={(e) => e.preventDefault()} onClick={() => pick(null)}>×</button>
            </li>
          )}
          {options.map((option) => (
            <li key={option.id}>
              <button
                type="button"
                className={`sia-inline-picker-item${value?.id === option.id ? " sia-inline-picker-item-selected" : ""}`}
                aria-pressed={value?.id === option.id}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(option)}
              >
                {option.label}
              </button>
            </li>
          ))}
          {!loading && options.length === 0 && <li className="sia-inline-picker-empty">…</li>}
          {footer && <li className="sia-inline-picker-footer">{footer}</li>}
        </ul>
      )}
    </div>
  );
}
