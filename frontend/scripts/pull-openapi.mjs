// Refreshes openapi/openapi.json from a running backend, then regenerates the TypeScript types.
//   npm run api:pull                       (defaults to the local dev server)
//   OPENAPI_URL=https://… npm run api:pull
import { writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const url = process.env.OPENAPI_URL ?? "http://localhost:8099/siamois/v3/api-docs";
const response = await fetch(url);
if (!response.ok) throw new Error(`${url} answered ${response.status}`);
const spec = await response.json();
writeFileSync(new URL("../openapi/openapi.json", import.meta.url), JSON.stringify(spec, null, 2) + "\n");
console.log(`openapi/openapi.json written from ${url} (${Object.keys(spec.paths).length} paths)`);
execFileSync("npm", ["run", "api:gen"], { stdio: "inherit" });
