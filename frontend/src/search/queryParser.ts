// The search bar's query language: free text plus `keyword:value` tokens (`projet:ABC`,
// `projet:"fouille 2024"`). A `word:` that is not a known keyword stays plain text, so searching
// "12:30" or an identifier with a colon still works.

export interface SearchKeyword {
  // The canonical key sent to the API (`filters=<key>:<value>`).
  key: string;
  // Everything the user may type for it, lower case.
  names: readonly string[];
}

export const SEARCH_KEYWORDS: readonly SearchKeyword[] = [{ key: "projet", names: ["projet", "project"] }];

export interface SearchFilter {
  key: string;
  value: string;
}

export interface ParsedQuery {
  // The free text left once the keyword tokens are removed, words joined by a single space.
  text: string;
  filters: SearchFilter[];
}

function keywordOf(name: string): SearchKeyword | undefined {
  const lower = name.toLowerCase();
  return SEARCH_KEYWORDS.find((keyword) => keyword.names.includes(lower));
}

/** Splits on whitespace, keeping a double-quoted run (after a `key:`) together. */
function tokenize(input: string): string[] {
  const tokens: string[] = [];
  let current = "";
  let quoted = false;
  for (const char of input) {
    if (char === '"') {
      quoted = !quoted;
      current += char;
    } else if (/\s/.test(char) && !quoted) {
      if (current) tokens.push(current);
      current = "";
    } else {
      current += char;
    }
  }
  if (current) tokens.push(current);
  return tokens;
}

export function parseQuery(input: string): ParsedQuery {
  const words: string[] = [];
  const filters: SearchFilter[] = [];
  for (const token of tokenize(input)) {
    const colon = token.indexOf(":");
    const keyword = colon > 0 ? keywordOf(token.slice(0, colon)) : undefined;
    const value = colon > 0 ? token.slice(colon + 1).replace(/"/g, "").trim() : "";
    if (keyword && value) filters.push({ key: keyword.key, value });
    else words.push(token);
  }
  return { text: words.join(" "), filters };
}

/** The `key:value` form the API takes (and the user types), quoted when the value has spaces. */
export function formatFilter(filter: SearchFilter): string {
  return /\s/.test(filter.value) ? `${filter.key}:"${filter.value}"` : `${filter.key}:${filter.value}`;
}

/** The keyword a half-typed last word starts (`proj` → projet), to offer it as a completion. */
export function keywordCompletion(input: string): SearchKeyword | undefined {
  const last = input.split(/\s+/).pop()?.toLowerCase() ?? "";
  if (!last || last.includes(":")) return undefined;
  return SEARCH_KEYWORDS.find((keyword) => keyword.names.some((name) => name.startsWith(last) && name !== last));
}
