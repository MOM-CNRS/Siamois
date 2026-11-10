import {type KeyboardEvent, useEffect, useRef} from "react";
import {InputNumber, type InputNumberChangeEvent, type InputNumberProps} from "primereact/inputnumber";

// PrimeReact's InputNumber (10.9) takes a "-" typed into an empty input, or over its whole selected
// value, for a decimal separator: its isFloat("-") is true ("-" % 1 is NaN), so the input becomes
// "0" and the next digits give a positive number. A field that accepts negatives could not be
// given one by typing it. This wrapper puts the lone "-" in the input itself and keeps it there
// until a digit follows; everything else is the plain InputNumber.

/** Whether a field bounded below by {@code min} accepts negative numbers. */
function acceptsNegatives(min: number | null | undefined): boolean {
  return min == null || min < 0;
}

function isWholeValueReplaced(input: HTMLInputElement): boolean {
  return input.value.length === 0 || (input.selectionStart === 0 && input.selectionEnd === input.value.length);
}

function showLoneMinus(input: HTMLInputElement) {
  input.value = "-";
  input.setSelectionRange(1, 1);
}

/**
 * An InputNumber that also accepts a negative number typed from an empty input (or over its whole
 * selected value), which PrimeReact turns into 0 on its own. Its onChange only ever reports a
 * number or null, never the lone "-" PrimeReact can emit while a negative number is being typed.
 *
 * @param props the InputNumber props, passed through unchanged apart from onChange and onKeyDown
 * @returns the number input
 */
export function SignedInputNumber(props: InputNumberProps) {
  const { onChange, onKeyDown, min, value } = props;
  const inputRef = useRef<HTMLInputElement>(null);
  // Set while the input shows only the "-" this wrapper put there: a re-render with no value would
  // otherwise make InputNumber clear it.
  const minusPending = useRef(false);

  useEffect(() => {
    const input = inputRef.current;
    if (minusPending.current && value == null && input && input.value !== "-") showLoneMinus(input);
  });

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    onKeyDown?.(event);
    if (event.defaultPrevented || event.key !== "-" || !acceptsNegatives(min)) return;
    const input = event.currentTarget;
    if (!isWholeValueReplaced(input)) return;
    event.preventDefault();
    showLoneMinus(input);
    minusPending.current = true;
    // The value the "-" replaced is gone: the answer is empty until a digit follows.
    if (value != null) onChange?.({ originalEvent: event, value: null } as InputNumberChangeEvent);
  }

  function handleChange(event: InputNumberChangeEvent) {
    minusPending.current = false;
    onChange?.({ ...event, value: typeof event.value === "number" ? event.value : null });
  }

  return (
    <InputNumber
      {...props}
      inputRef={inputRef}
      onKeyDown={handleKeyDown}
      onChange={handleChange}
      onBlur={(event) => {
        minusPending.current = false;
        props.onBlur?.(event);
      }}
    />
  );
}
