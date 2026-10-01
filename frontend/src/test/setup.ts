import { afterEach } from "vitest";
import { queryClient } from "../api/queryClient";

// Vitest setup shared by every test file.

// React only checks act() usage when told it runs under a test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// jsdom's CSS parser can't read the stylesheets PrimeReact injects at runtime (cascade layers) and
// reports each one through console.error. They carry no information for the tests and would bury
// real warnings, so they're dropped here — and only them.
const consoleError = console.error.bind(console);
console.error = (...args: unknown[]) => {
  const first = args[0];
  const text = first instanceof Error ? first.message : String(first);
  if (text.includes("Could not parse CSS stylesheet")) return;
  consoleError(...args);
};

// The app's shared cache (catalogs are fetched through it) must not carry one test's data into the next.
afterEach(() => queryClient.clear());
