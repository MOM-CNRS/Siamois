// Dev-only: an in-browser fake of the /api/** endpoints the main panel calls, so the REAL panels
// (App → EntityListPanel / EntityDetailPanel) can be rendered under the real host stylesheets
// without a backend. Shapes follow the Java resources (see docs/api-changes-vs-main.md). Unknown
// endpoints answer an empty list and are logged, so a missing fixture is visible in the console.

const FIELDS: Record<string, unknown> = {
  "-1": { id: "-1", resourceType: "fields", label: "Nom", answerType: "TEXT", isSystemField: true, valueBinding: "name", icon: "bi bi-question", query: { sortable: true, filterOp: "contains" } },
  "-2": { id: "-2", resourceType: "fields", label: "Identifiant", answerType: "TEXT", isSystemField: true, valueBinding: "fullIdentifier", icon: "bi bi-question", query: { sortable: true, filterOp: "contains" } },
  "-3": { id: "-3", resourceType: "fields", label: "Date de début", answerType: "DATETIME", isSystemField: true, valueBinding: "beginDate", icon: "bi bi-calendar", query: { sortable: true, filterOp: "date-range" } },
  "-4": { id: "-4", resourceType: "fields", label: "Date de fin", answerType: "DATETIME", isSystemField: true, valueBinding: "endDate", icon: "bi bi-calendar" },
  "10": { id: "10", resourceType: "fields", label: "Commentaire", answerType: "TEXT", isSystemField: false, isTextArea: true, icon: "bi bi-chat" },
  "11": { id: "11", resourceType: "fields", label: "Surface (m²)", answerType: "DECIMAL", isSystemField: false, icon: "bi bi-rulers", constraints: { min: 0 } },
  // A recording unit's relation fields (shown after the project and type columns in the real
// defaults; this harness has neither): every list row carries a preview and a total (MultiValue).
  "-319": { id: "-319", resourceType: "fields", label: "Parents", answerType: "SELECT_MULTIPLE_RECORDING_UNIT", isSystemField: true, valueBinding: "parents", icon: "bi bi-pencil-square", query: { sortable: true, filterOp: "in" } },
  "-320": { id: "-320", resourceType: "fields", label: "Contient", answerType: "SELECT_MULTIPLE_RECORDING_UNIT", isSystemField: true, valueBinding: "children", icon: "bi bi-pencil-square", query: { sortable: true, filterOp: "in" } },
  "-327": { id: "-327", resourceType: "fields", label: "Relations stratigraphiques", answerType: "SELECT_MULTIPLE_STRATIGRAPHY", isSystemField: true, valueBinding: "stratigraphicRelationships", icon: "bi bi-layers", readOnly: true, query: { sortable: true, filterOp: "in" } },
  "-326": { id: "-326", resourceType: "fields", label: "Mobilier", answerType: "SELECT_MULTIPLE_SPECIMEN", isSystemField: true, valueBinding: "specimenList", icon: "bi bi-bucket", readOnly: true, query: { sortable: true, filterOp: "in" } },
};

const RELATION_COLUMNS = [
  { columnId: "fullIdentifier", fieldId: "-2", visible: true, order: 0 },
  { columnId: "isPartOf", fieldId: "-319", visible: true, order: 1 },
  { columnId: "contains", fieldId: "-320", visible: true, order: 2 },
  { columnId: "relationships", fieldId: "-327", visible: true, order: 3 },
  { columnId: "specimen", fieldId: "-326", visible: true, order: 4 },
];

const usRef = (n: number) => ({ resourceId: String(100 + n), resourceType: "recording-units", label: `2026-OA1000-US${n + 1}` });
const findRef = (n: number) => ({ resourceId: String(500 + n), resourceType: "finds", label: `2026-OA1000-M${n + 1}` });
const stratiRef = (n: number) => ({
  ...usRef(n),
  qualifier: { concept: { resourceId: "164", resourceType: "concepts", label: ["coupe", "recouvre", "synchrone de"][n % 3] }, role: "unit1", position: n % 3 === 2 ? "synchronous" : "posterior", asynchronous: n % 3 !== 2, uncertain: n % 4 === 3 },
});
// MultiValue as the list serves it (valuesLimit=1): the first value, the total, and the link to the rest.
function preview(unitId: number, fieldId: string, all: unknown[]) {
  return {
    values: all.slice(0, 1),
    total: all.length,
    complete: all.length <= 1,
    ...(all.length > 1 ? { _links: { values: `/api/v1/recording-units/${unitId}/fields/${fieldId}/values` } } : {}),
  };
}
function relationValues(unitId: number, fieldId: string): unknown[] {
  const i = unitId - 100;
  const n = (k: number) => Array.from({ length: k }, (_, j) => j);
  switch (fieldId) {
    case "-319": return n(i % 2).map((j) => usRef(j));
    case "-320": return n(i % 4).map((j) => usRef(j + 1));
    case "-327": return n(i % 5 === 0 ? 0 : i + 2).map((j) => stratiRef(j));
    case "-326": return n(i * 7).map((j) => findRef(j));
    default: return [];
  }
}

const LAYOUT = [
  {
    name: "common.header.general",
    rows: [
      { columns: [
        { fieldId: -1, width: { span: 12, md: 6, lg: 3 }, isRequired: true, isReadOnly: false },
        { fieldId: -2, width: { span: 12, md: 6, lg: 3 }, isRequired: false, isReadOnly: true },
      ] },
      { columns: [
        { fieldId: -3, width: { span: 12, md: 6, lg: 3 }, isRequired: false, isReadOnly: false },
        { fieldId: -4, width: { span: 12, md: 6, lg: 3 }, isRequired: false, isReadOnly: false },
      ] },
    ],
  },
  {
    name: "actionunit.header.documentation",
    rows: [{ columns: [
      { fieldId: 10, width: { span: 12 }, isRequired: false, isReadOnly: false },
      { fieldId: 11, width: { span: 12, md: 4 }, isRequired: false, isReadOnly: false },
    ] }],
  },
];

const TYPE = (label: string) => ({ resourceType: "concepts", id: label.length.toString(), resolvedLabel: label });
const PERMS = { canEdit: true, canDelete: true, canManageSettings: true, canValidate: true };

// ?rows=N: a bigger project list, to exercise the paginator and large pages.
const PROJECT_COUNT = Number(new URLSearchParams(location.search).get("rows") ?? 14);

const projects = Array.from({ length: PROJECT_COUNT }, (_, i) => ({
  resourceType: "projects",
  id: String(i + 1),
  name: ["Fouille du Mont Beuvray", "Diagnostic Rue Lafayette", "Nécropole de Saint-Denis", "Villa gallo-romaine"][i % 4] + (i > 3 ? ` ${i}` : ""),
  identifier: `OA${1000 + i}`,
  fullIdentifier: `2026-OA${1000 + i}`,
  beginDate: "2026-03-12",
  endDate: i % 3 ? "2026-07-01" : null,
  validated: (["INCOMPLETE", "COMPLETE", "VALIDATED", "CANCELLED"] as const)[i % 4],
  type: TYPE(["Fouille préventive", "Diagnostic", "Prospection"][i % 3]),
  mainLocation: { resourceType: "places", id: "7", name: "Bibracte" },
  spatialContext: [{ resourceType: "places", id: "7", name: "Bibracte" }],
  organization: { resourceType: "organizations", id: "1" },
  _counts: { children: 0, recordingUnits: 12 + i, finds: 40, phases: 3, containers: 5 },
  _permissions: PERMS,
  bookmarked: i === 1,
  resourceUri: `/action-unit/${i + 1}`,
  answers: { "10": "Campagne 2026, secteur nord.", "11": 125.5 },
}));

const recordingUnits = Array.from({ length: 8 }, (_, i) => ({
  resourceType: "recording-units",
  id: String(100 + i),
  identifier: String(i + 1),
  fullIdentifier: `2026-OA1000-US${i + 1}`,
  projectId: "1",
  type: TYPE(["Couche", "Fosse", "Mur"][i % 3]),
  openingDate: "2026-04-02",
  closingDate: null,
  validated: "INCOMPLETE",
  organization: { resourceType: "organizations", id: "1" },
  _counts: { specimen: 2, children: 1, parents: 0, relationships: 3 },
  _permissions: PERMS,
  resourceUri: `/recording-unit/${100 + i}`,
  bookmarked: false,
  answers: Object.fromEntries(["-319", "-320", "-327", "-326"].map((f) => [f, preview(100 + i, f, relationValues(100 + i, f))])),
}));

// `answers` projected to the requested `fields=`, like the real list endpoints — so a column shown
// later arrives through its own request and can be seen merging in.
function project(row: unknown, fields: string[]) {
  const answers = (row as { answers?: Record<string, unknown> }).answers;
  if (!answers) return row;
  return { ...(row as object), answers: Object.fromEntries(Object.entries(answers).filter(([k]) => fields.includes(k))) };
}

function list(data: unknown[], url: URL) {
  const offset = Number(url.searchParams.get("offset") ?? 0);
  const limit = Number(url.searchParams.get("limit") ?? 20);
  const fields = (url.searchParams.get("fields") ?? "").split(",").filter(Boolean);
  return { data: data.slice(offset, offset + limit).map((r) => project(r, fields)), meta: { total: data.length, limit, offset } };
}

const routes: [RegExp, (m: RegExpMatchArray, url: URL, init?: RequestInit) => unknown][] = [
  [/^\/api\/auth\/session-token$/, () => ({ accessToken: "dev-harness", expiresIn: 3600, tokenType: "Bearer" })],
  [/^\/api\/v1\/organizations\/\d+\/counts$/, () => ({ data: { projects: projects.length, places: 9, recordingUnits: 240, finds: 1200, phases: 18, containers: 40 } })],
  [/^\/api\/v1\/organizations\/\d+\/project-types$/, () => ({
    data: [],
    form: { resourceType: "forms", layoutJson: JSON.stringify(LAYOUT) },
    fieldConfigs: [],
    tableColumns: [
      { columnId: "name", fieldId: "-1", visible: true, order: 0 },
      { columnId: "fullIdentifier", fieldId: "-2", visible: true, order: 1 },
      { columnId: "beginDate", fieldId: "-3", visible: true, order: 2 },
      { columnId: "comment", fieldId: "10", visible: false, order: 3 },
    ],
    fields: FIELDS,
  })],
  [/^\/api\/v1\/bookmarks\/status$/, () => ({ bookmarked: false })],
  [/^\/api\/v1\/projects$/, (_m, url) => list(projects, url)],
  [/^\/api\/v1\/projects\/(\d+)\/siblings$/, (m) => {
    const i = Number(m[1]) - 1;
    const ref = (p: (typeof projects)[number]) => ({ id: p.id, label: p.name, resourceUri: p.resourceUri });
    return { data: { previous: ref(projects[(i + projects.length - 1) % projects.length]), next: ref(projects[(i + 1) % projects.length]) } };
  }],
  [/^\/api\/v1\/projects\/(\d+)\/history$/, () => ({ data: [{ revisionDate: "2026-09-20T10:12:00Z", revisionType: "MOD", author: { id: 1, name: "Grégory", lastname: "B." } }], meta: { total: 1, limit: 50, offset: 0 } })],
  [/^\/api\/v1\/projects\/(\d+)\/recording-units$/, (_m, url) => list(recordingUnits, url)],
  [/^\/api\/v1\/(?:projects|organizations)\/(\d+)\/recording-unit-types$/, () => ({ data: [{ id: "1", formBundle: { resourceType: "forms", layoutJson: JSON.stringify(LAYOUT) }, fields: FIELDS }], tableColumns: RELATION_COLUMNS, fields: FIELDS })],
  [/^\/api\/v1\/recording-units\/(\d+)\/fields\/(-?\d+)\/values$/, (m, url) => {
    const all = relationValues(Number(m[1]), m[2]);
    const offset = Number(url.searchParams.get("offset") ?? 0);
    const limit = Number(url.searchParams.get("limit") ?? 50);
    return { data: all.slice(offset, offset + limit), meta: { total: all.length, limit, offset } };
  }],
  [/^\/api\/v1\/recording-units$/, (_m, url) => list(recordingUnits, url)],
  [/^\/api\/v1\/recording-units\/(\d+)\/siblings$/, () => ({ data: { previous: { id: "100", label: "US1", resourceUri: "/recording-unit/100" }, next: { id: "101", label: "US2", resourceUri: "/recording-unit/101" } } })],
  [/^\/api\/v1\/recording-units\/(\d+)\/(children|parents)$/, (_m, url) => list(recordingUnits.slice(0, 2), url)],
  [/^\/api\/v1\/recording-units\/(\d+)$/, (m) => ({ data: { ...(recordingUnits.find((r) => r.id === m[1]) ?? recordingUnits[0]), name: "Couche d'occupation", answers: { "10": "Sol damé, charbons." } } })],
  [/^\/api\/v1\/projects\/(\d+)$/, (m, _url, init) => {
    const p = projects.find((x) => x.id === m[1]) ?? projects[0];
    if (init?.method === "PATCH" && init.body) Object.assign(p, JSON.parse(String(init.body)));
    return { data: p };
  }],
];

const realFetch = window.fetch.bind(window);

window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, location.origin);
  if (!url.pathname.startsWith("/api/")) return realFetch(input, init);
  await new Promise((r) => setTimeout(r, 120)); // a little latency, so loading states show up
  for (const [re, handler] of routes) {
    const m = url.pathname.match(re);
    if (m) return new Response(JSON.stringify(handler(m, url, init)), { status: 200, headers: { "Content-Type": "application/json" } });
  }
  console.warn("[mockApi] no fixture for", init?.method ?? "GET", url.pathname + url.search);
  return new Response(JSON.stringify({ data: [], meta: { total: 0, limit: 0, offset: 0 } }), { status: 200, headers: { "Content-Type": "application/json" } });
};
