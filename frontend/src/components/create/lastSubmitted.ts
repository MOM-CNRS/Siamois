const PREFIX = "siamois.create.last.";

// What the last creation of a kind used (its project, its type): a browser-side convenience, so
// unreadable or unwritable storage just means nothing is remembered.
export function readLastSubmitted<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function writeLastSubmitted(key: string, value: unknown) {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch {
    // Storage full or blocked: the next form simply starts empty.
  }
}
