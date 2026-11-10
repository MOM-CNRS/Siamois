import {getLocale, type Locale} from "./index";

// Dates follow the region of the chosen language. The page only knows a language code (LangBean's
// getLanguageCode(), no country), so a language maps to a region only when that is unambiguous:
// French → France (DD/MM/YYYY, 24-hour clock). English is spoken with day-first (GB) and
// month-first (US) orders, so it — and any language added without an entry here — falls back to
// ISO 8601 (YYYY-MM-DD, 24-hour clock).
const REGIONAL_TAGS: Partial<Record<Locale, string>> = { fr: "fr-FR" };

// PrimeReact Calendar patterns for the same orders ("yy" is a 4-digit year there).
const CALENDAR_FORMATS: Record<string, string> = { "fr-FR": "dd/mm/yy" };

/** The BCP 47 tag dates are formatted with, or null when the region is ambiguous (ISO 8601). */
export function dateLocale(locale: Locale = getLocale()): string | null {
  return REGIONAL_TAGS[locale] ?? null;
}

/** The PrimeReact Calendar `dateFormat` matching formatDate ("yy" is a 4-digit year there). */
export function calendarDateFormat(locale: Locale = getLocale()): string {
  const tag = dateLocale(locale);
  return (tag && CALENDAR_FORMATS[tag]) ?? "yy-mm-dd";
}

/** The PrimeReact Calendar `hourFormat` matching formatDateTime: "12" where the region uses a 12-hour clock, "24" otherwise (ISO 8601 included). */
export function calendarHourFormat(locale: Locale = getLocale()): "12" | "24" {
  const tag = dateLocale(locale);
  return tag && new Intl.DateTimeFormat(tag, { hour: "numeric" }).resolvedOptions().hour12 ? "12" : "24";
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

// A calendar day is read from the value's own "yyyy-MM-dd" part, not converted to local time: the
// server sends a date-only value as midnight UTC, which is the previous day anywhere west of UTC.
function toDay(value: string | Date): Date | null {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  const day = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (day) return new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3]));
  return toInstant(value);
}

function toInstant(value: string | Date): Date | null {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function isoDay(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/**
 * A calendar day in the chosen language's regional format ("15/01/2024" in French), ISO 8601
 * ("2024-01-15") when the region is ambiguous. An unparsable string is returned as is; null → "".
 */
export function formatDate(value: string | Date | null | undefined, locale: Locale = getLocale()): string {
  if (value == null || value === "") return "";
  const date = toDay(value);
  if (!date) return String(value);
  const tag = dateLocale(locale);
  if (!tag) return isoDay(date);
  return new Intl.DateTimeFormat(tag, { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

/**
 * An instant, in local time, in the chosen language's regional format ("15/01/2024 14:30" in
 * French), ISO 8601 ("2024-01-15 14:30") when the region is ambiguous.
 */
export function formatDateTime(value: string | Date | null | undefined, locale: Locale = getLocale()): string {
  if (value == null || value === "") return "";
  const date = toInstant(value);
  if (!date) return String(value);
  const tag = dateLocale(locale);
  if (!tag) return `${isoDay(date)} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  const day = new Intl.DateTimeFormat(tag, { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
  const time = new Intl.DateTimeFormat(tag, { hour: "2-digit", minute: "2-digit" }).format(date);
  return `${day} ${time}`;
}
