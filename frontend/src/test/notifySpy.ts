import {vi} from "vitest";

/** What the app toasted during a test: every file runs against this instead of the PrimeReact Toast (see setup.ts). */
export const taskToastSpy = { progress: vi.fn(), close: vi.fn() };
export const notifySpy = { error: vi.fn(), success: vi.fn(), warn: vi.fn(), info: vi.fn(), task: vi.fn(() => taskToastSpy) };
