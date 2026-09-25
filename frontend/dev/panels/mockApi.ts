// Dev-only: an in-browser fake of the /api/** endpoints the main panel calls, so the REAL panels
// (App → EntityListPanel / EntityDetailPanel) can be rendered under the real host stylesheets
// without a backend. Shapes follow the Java resources (see docs/api-changes-vs-main.md). Unknown
// endpoints answer an empty list and are logged, so a missing fixture is visible in the console.

const FIELDS: Record<string, unknown> = {
  "-1": { id: "-1", resourceType: "fields", label: "Nom", answerType: "TEXT", isSystemField: true, valueBinding: "name", icon: "bi bi-question", query: { sortable: true, filterOp: "contains" } },
  "-2": { id: "-2", resourceType: "fields", label: "Identifiant", answerType: "TEXT", isSystemField: true, valueBinding: "fullIdentifier", icon: "bi bi-question", query: { sortable: true, filterOp: "contains" } },
  "-3": { id: "-3", resourceType: "fields", label: "Date de début", answerType: "DATE", isSystemField: true, valueBinding: "beginDate", icon: "bi bi-calendar", query: { sortable: true, filterOp: "date-range" } },
  "-4": { id: "-4", resourceType: "fields", label: "Date de fin", answerType: "DATE", isSystemField: true, valueBinding: "endDate", icon: "bi bi-calendar" },
  "10": { id: "10", resourceType: "fields", label: "Commentaire", answerType: "TEXT", isSystemField: false, isTextArea: true, icon: "bi bi-chat" },
  "11": { id: "11", resourceType: "fields", label: "Surface (m²)", answerType: "DECIMAL", isSystemField: false, icon: "bi bi-rulers", constraints: { min: 0 } },
};

const LAYOUT = [
  {
    name: "common.header.general",
    rows: [
      { columns: [
        { fieldId: -1, width: { span: 12, md: 6 }, isRequired: true, isReadOnly: false },
        { fieldId: -2, width: { span: 12, md: 6 }, isRequired: false, isReadOnly: true },
      ] },
      { columns: [
        { fieldId: -3, width: { span: 12, md: 6 }, isRequired: false, isReadOnly: false },
        { fieldId: -4, width: { span: 12, md: 6 }, isRequired: false, isReadOnly: false },
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

const projects = Array.from({ length: 14 }, (_, i) => ({
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
}));

function list(data: unknown[], url: URL) {
  const offset = Number(url.searchParams.get("offset") ?? 0);
  const limit = Number(url.searchParams.get("limit") ?? 20);
  return { data: data.slice(offset, offset + limit), meta: { total: data.length, limit, offset } };
}

const routes: [RegExp, (m: RegExpMatchArray, url: URL, init?: RequestInit) => unknown][] = [
  [/^\/api\/auth\/session-token$/, () => ({ accessToken: "dev-harness", expiresIn: 3600, tokenType: "Bearer" })],
  [/^\/api\/v1\/organizations\/\d+\/counts$/, () => ({ data: { projects: projects.length, places: 9, recordingUnits: 240, finds: 1200, phases: 18, containers: 40 } })],
  [/^\/api\/v1\/organizations\/\d+\/project-types$/, () => ({
    data: [],
    _default: {
      form: { resourceType: "forms", layoutJson: JSON.stringify(LAYOUT) },
      fieldConfigs: [],
      tableColumns: [
        { columnId: "name", fieldId: "-1", visible: true, order: 0 },
        { columnId: "fullIdentifier", fieldId: "-2", visible: true, order: 1 },
        { columnId: "beginDate", fieldId: "-3", visible: true, order: 2 },
        { columnId: "comment", fieldId: "10", visible: false, order: 3 },
      ],
    },
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
  [/^\/api\/v1\/projects\/(\d+)\/recording-unit-types$/, () => ({ data: [], _default: { formBundle: { resourceType: "forms", layoutJson: JSON.stringify(LAYOUT) }, tableColumns: [{ columnId: "fullIdentifier", fieldId: "-2", visible: true, order: 0 }], fields: FIELDS }, fields: FIELDS })],
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
