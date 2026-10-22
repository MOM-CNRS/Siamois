import { vi } from "vitest";

/** What the app toasted during a test: every file runs against this instead of the PrimeReact Toast (see setup.ts). */
export const notifySpy = { error: vi.fn(), success: vi.fn(), warn: vi.fn(), info: vi.fn() };
