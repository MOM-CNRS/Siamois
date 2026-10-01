import { en } from "./en";
import { fr, type MessageKey } from "./fr";

export type { MessageKey };
export type Locale = "fr" | "en";

export const DEFAULT_LOCALE: Locale = "fr";
const catalogs: Record<Locale, Record<MessageKey, string>> = { fr, en };

/** "en", "en-GB", "EN" → "en"; anything the bundle has no catalog for → the default. */
export function resolveLocale(code: string | null | undefined): Locale {
  const language = (code ?? "").trim().toLowerCase().slice(0, 2);
  return language === "en" || language === "fr" ? language : DEFAULT_LOCALE;
}

// The JSF page's language, read when the bundle loads: the bundle script runs before the mount div
// exists, but template.xhtml writes langBean's language on <html> (xml:lang). Module-level text
// (entity labels, column headers) is evaluated on import, so the locale has to be right by then;
// mount() confirms it from the mount div's own `data-locale`. A language change reloads the page.
let current: Locale = resolveLocale(
  typeof document === "undefined"
    ? null
    : (document.documentElement.getAttribute("lang") ?? document.documentElement.getAttribute("xml:lang")),
);

export function setLocale(locale: Locale): void {
  current = locale;
}

export function getLocale(): Locale {
  return current;
}

/** The BCP 47 tag for `Intl` and `toLocale*String` (dates, numbers). */
export function intlLocale(): string {
  return current === "en" ? "en-GB" : "fr-FR";
}

export type MessageParams = Record<string, string | number>;

function interpolate(message: string, params?: MessageParams): string {
  if (!params) return message;
  return message.replace(/\{(\w+)\}/g, (whole, name: string) => (name in params ? String(params[name]) : whole));
}

function lookup(key: string): string {
  return (
    (catalogs[current] as Record<string, string>)[key] ??
    (catalogs[DEFAULT_LOCALE] as Record<string, string>)[key] ??
    key
  );
}

/** The message of `key` in the current language, `{name}` placeholders filled from `params`. */
export function t(key: MessageKey, params?: MessageParams): string {
  return interpolate(lookup(key), params);
}

type PluralBase<K> = K extends `${infer B}_other` ? B : never;
export type PluralKey = PluralBase<MessageKey>;

/**
 * The message of `key` for `count`: picks `<key>_one` or `<key>_other` by the language's plural
 * rules (French counts 0 as singular, English does not); `{count}` is filled in.
 */
export function tn(key: PluralKey, count: number, params?: MessageParams): string {
  const category = new Intl.PluralRules(intlLocale()).select(count);
  const specific = `${key}_${category}`;
  const message = specific in catalogs[current] ? lookup(specific) : lookup(`${key}_other`);
  return interpolate(message, { count, ...params });
}
