import {useLayoutEffect, useRef, useState, useSyncExternalStore} from "react";
import {useQuery} from "@tanstack/react-query";
import {Button} from "primereact/button";
import {InputText} from "primereact/inputtext";
import {getFicheConfig} from "../api/resources/ficheConfigs";
import {listViews, type ViewResource} from "../api/resources/views";
import {queryKeys} from "../api/queryKeys";
import {PROJECT_KEY} from "../entities/keys";
import {entityColor} from "../fields/display";
import {getAllEntityTypes, getEntityType} from "../entities/registry";
import {FilterChipBar, type FilterSpec} from "../components/table/FilterChipBar";
import type {FilterOption} from "../fields/optionSources";
import type {FilterValue} from "../panels/list/tableState";
import {editableTabs} from "../panels/detail/ficheTabsModel";
import {ficheScopeOf} from "../panels/detail/ficheConfig";
import {getContext, getNavigation, subscribeContext} from "../navigation/externalBus";
import {t, tn} from "../i18n";
import {groupViews, matches, projectOfView} from "./viewsTree";

const PAGE = 200;

/** Every saved view of the organization (the server pages them 200 at a time). */
async function listAllViews(organizationId: number): Promise<ViewResource[]> {
  const views: ViewResource[] = [];
  for (let offset = 0; ; offset += PAGE) {
    const page = await listViews(organizationId, { limit: PAGE, offset });
    views.push(...page.data);
    if (page.data.length === 0 || views.length >= page.totalCount) return views;
  }
}

const tableOf = (key: string) => getEntityType(key);
const openTable = (key: string, viewId?: number) => getNavigation()?.openView(key, viewId);

const colorOf = (key: string | undefined) => ({ "--sia-row-color": entityColor(key) ?? "var(--siamois-green)" }) as React.CSSProperties;

/** One line of the tree: an optional chevron (toggles the children), the coloured icon, the label (opens). */
function Row({ icon, label, hint, onOpen, level = 0, color, expanded, onToggle }: {
  icon: string; label: string; hint?: string; onOpen: () => void; level?: number; color?: string;
  expanded?: boolean; onToggle?: () => void;
}) {
  return (
    <div className="sia-views-row" style={{ paddingLeft: 8 + level * 16, ...colorOf(color) }}>
      <button type="button" className="sia-views-chevron" aria-label={label} aria-expanded={expanded} disabled={!onToggle} onClick={onToggle}>
        {onToggle && <i className={expanded ? "bi bi-chevron-down" : "bi bi-chevron-right"} />}
      </button>
      <button type="button" className="sia-views-open" title={hint ?? label} onClick={onOpen}>
        <i className={icon} />
        <span className="sia-views-row-label">{label}</span>
      </button>
    </div>
  );
}

function UserViewRow({ view, level }: { view: ViewResource; level?: number }) {
  const table = tableOf(view.resourceType);
  return (
    <Row
      icon={table?.icon ?? "bi bi-file-earmark-spreadsheet"}
      color={view.resourceType}
      label={view.title}
      hint={view.ownerName ? t("views.drawer.by", { name: view.ownerName }) : view.title}
      level={level}
      onOpen={() => openTable(view.resourceType, view.id)}
    />
  );
}

/** The built-in and configured list tabs of one project's fiche, each opening that list for the project. */
function ProjectTabs({ organizationId, project, userViews }: { organizationId: number; project: ProjectRow; userViews: ViewResource[] }) {
  const scope = ficheScopeOf(PROJECT_KEY, project);
  const config = useQuery({
    queryKey: queryKeys.ficheConfig(organizationId, PROJECT_KEY, scope.projectId, scope.typeId),
    queryFn: () => getFicheConfig({ organizationId, resourceType: PROJECT_KEY, ...scope }),
    staleTime: 0,
  });
  const code = tableOf(PROJECT_KEY)?.detail?.tabs ?? [];
  const rows = editableTabs(code as never, config.data?.tabs)
    .filter((row) => !row.hidden)
    .map((row) => {
      const target = row.kind === "list" ? row.target : code.find((tab) => tab.key === row.key)?.relation?.target;
      return target ? { key: row.key, label: row.label || row.defaultLabel, target } : null;
    })
    .filter((row): row is { key: string; label: string; target: string } => row != null);
  return (
    <div>
      {rows.map((row) => (
        <Row
          key={row.key}
          level={2}
          icon={tableOf(row.target)?.icon ?? "bi bi-table"}
          color={row.target}
          label={row.label}
          onOpen={() => getNavigation()?.openList(row.target, { projectId: project.id })}
        />
      ))}
      {userViews.map((view) => (
        <UserViewRow key={view.id} view={view} level={2} />
      ))}
    </div>
  );
}

// Keys of the projects in the set of open nodes (a table's key is its entity type's).
const PROJECT_PREFIX = "project:";

interface ProjectRow {
  id: number | string;
  name?: string | null;
  type?: { id?: unknown } | null;
}

function Projects({ organizationId, projects, userViews, open, onToggle }: {
  organizationId: number; projects: ProjectRow[]; userViews: Map<string, ViewResource[]>; open: Set<string>; onToggle: (id: string) => void;
}) {
  if (projects.length === 0) return <div className="sia-views-empty" style={{ paddingLeft: 40 }}>{t("views.drawer.noProject")}</div>;
  return (
    <>
      {projects.map((project) => {
        const id = String(project.id);
        const expanded = open.has(PROJECT_PREFIX + id);
        return (
          <div key={id}>
            <Row
              level={1}
              icon={tableOf(PROJECT_KEY)?.icon ?? "bi bi-table"}
              color={PROJECT_KEY}
              label={project.name ?? id}
              expanded={expanded}
              onToggle={() => onToggle(PROJECT_PREFIX + id)}
              onOpen={() => getNavigation()?.navigate(PROJECT_KEY, project.id)}
            />
            {expanded && <ProjectTabs organizationId={organizationId} project={project} userViews={userViews.get(id) ?? []} />}
          </div>
        );
      })}
    </>
  );
}

/**
 * The filter chips of the drawer, those of a table's header. They stay on one line next to the expand and collapse
 * buttons; when they do not fit, they go behind a single « N filtres » chip that lists them in an overlay.
 */
function FilterRow(props: {
  specs: FilterSpec[];
  filters: Record<string, FilterValue>;
  optionsByKey: Record<string, FilterOption[]>;
  onChange: (key: string, value: FilterValue | undefined, options?: FilterOption[]) => void;
}) {
  const box = useRef<HTMLDivElement>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [width, setWidth] = useState(0);
  // What the chips say: a change of it changes the width they need.
  const content = JSON.stringify(props.filters);

  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const observer = new ResizeObserver(() => setWidth(el.clientWidth));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  // Whatever changes what the chips need, they are tried in full again.
  useLayoutEffect(() => setCollapsed(false), [width, content]);
  // Tried in full: if they overflow the line, they collapse.
  useLayoutEffect(() => {
    const el = box.current;
    if (!collapsed && el && el.scrollWidth > el.clientWidth + 1) setCollapsed(true);
  }, [collapsed, width, content]);

  return (
    <div className="sia-views-filters" ref={box}>
      <FilterChipBar {...props} maxVisible={collapsed ? 0 : undefined} moreLabel={(n) => tn("views.drawer.filters", n)} />
    </div>
  );
}

/**
 * The sidebar's « Vues » drawer: the tables (the system views), then each project with its own list tabs and
 * the user views tied to it, then the user-defined views of every table.
 */
export function ViewsDrawer({ organizationId }: { organizationId?: number }) {
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState<Set<string>>(new Set());
  // The project filter: undefined follows the project the main pane is on; null is « no project filter ».
  const [pickedProject, setPickedProject] = useState<FilterValue | null | undefined>(undefined);
  const [ownerFilter, setOwnerFilter] = useState<FilterValue | undefined>(undefined);
  const [optionsByKey, setOptionsByKey] = useState<Record<string, FilterOption[]>>({});
  const context = useSyncExternalStore(subscribeContext, () => getContext().projectId);
  const views = useQuery({
    queryKey: [...queryKeys.views(organizationId), "all"],
    queryFn: () => listAllViews(organizationId!),
    enabled: organizationId != null,
    staleTime: 0,
  });
  const projects = useQuery({
    queryKey: ["views-drawer", "projects", organizationId],
    queryFn: () => tableOf(PROJECT_KEY)!.api.list({ offset: 0, limit: 200, organizationId: organizationId!, sort: "name:asc" }),
    enabled: organizationId != null,
    staleTime: 0,
  });
  if (organizationId == null) return null;
  const searching = search.trim() !== "";
  const allProjects = (projects.data?.data ?? []) as ProjectRow[];
  const projectName = (id: string) => allProjects.find((p) => String(p.id) === id)?.name ?? id;
  const projectFilter: FilterValue | undefined =
    pickedProject === undefined
      ? context ? { op: "in", v: [context], labels: { [context]: projectName(context) } } : undefined
      : (pickedProject ?? undefined);
  const projectIds = projectFilter?.op === "in" ? projectFilter.v : [];
  const ownerIds = ownerFilter?.op === "in" ? ownerFilter.v : [];
  const shownProjects = allProjects.filter(
    (p) => (projectIds.length === 0 || projectIds.includes(String(p.id))) && (!searching || matches(p.name ?? "", search)),
  );
  // A view tied to another project is hidden by the project filter, the views tied to none stay; the user filter
  // keeps the views of the users picked.
  const all = (views.data ?? []).filter((v) => {
    const projectId = projectOfView(v);
    return (projectIds.length === 0 || projectId == null || projectIds.includes(projectId))
      && (ownerIds.length === 0 || (v.ownerId != null && ownerIds.includes(String(v.ownerId))));
  });
  const owners = new Map<string, string>();
  for (const v of views.data ?? []) if (v.ownerId != null) owners.set(String(v.ownerId), v.ownerName ?? String(v.ownerId));
  const pick = (options: FilterOption[]) => async (q?: string) =>
    options.filter((o) => !q || o.label.toLowerCase().includes(q.toLowerCase()));
  const specs: FilterSpec[] = [
    {
      key: "project",
      label: t("views.drawer.filterProject"),
      kind: "in",
      loadOptions: pick(allProjects.map((p) => ({ id: String(p.id), label: p.name ?? String(p.id) }))),
    },
    {
      key: "owner",
      label: t("views.drawer.filterUser"),
      kind: "in",
      loadOptions: pick([...owners].map(([id, label]) => ({ id, label }))),
    },
  ];
  const filters: Record<string, FilterValue> = {
    ...(projectFilter ? { project: projectFilter } : {}),
    ...(ownerFilter ? { owner: ownerFilter } : {}),
  };
  const onFilterChange = (key: string, value: FilterValue | undefined, options?: FilterOption[]) => {
    if (options) setOptionsByKey((prev) => ({ ...prev, [key]: options }));
    if (key === "project") setPickedProject(value ?? null);
    else setOwnerFilter(value);
  };
  const { byProject } = groupViews(all);
  const tables = getAllEntityTypes().filter((config) => config.routes?.list != null);
  const withViews = (key: string) => all.filter((v) => v.resourceType === key && (!searching || matches(v.title, search)));
  const toggle = (key: string) => setOpen((prev) => { const next = new Set(prev); if (!next.delete(key)) next.add(key); return next; });
  const expandAll = () =>
    setOpen(new Set([
      ...tables.filter((c) => c.key === PROJECT_KEY || withViews(c.key).length > 0).map((c) => c.key),
      ...shownProjects.map((p) => PROJECT_PREFIX + p.id),
    ]));
  return (
    <div className="sia-views-drawer">
      <div className="sia-views-search">
        <InputText value={search} onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === "Enter" && e.preventDefault()} placeholder={t("views.drawer.search")} style={{ width: "100%" }} />
        <div className="sia-views-tools">
          <FilterRow specs={specs} filters={filters} optionsByKey={optionsByKey} onChange={onFilterChange} />
          <Button type="button" text size="small" icon="bi bi-chevron-double-down" aria-label={t("views.drawer.expandAll")} tooltip={t("views.drawer.expandAll")} onClick={expandAll} />
          <Button type="button" text size="small" icon="bi bi-chevron-double-up" aria-label={t("views.drawer.collapseAll")} tooltip={t("views.drawer.collapseAll")} onClick={() => setOpen(new Set())} />
        </div>
      </div>
      <div className="sia-views-body">
        {tables.map((config) => {
          const isProject = config.key === PROJECT_KEY;
          const own = withViews(config.key);
          // A search keeps the tables that match, or hold a matching view or project.
          if (searching && !matches(config.labels.plural, search) && own.length === 0 && !(isProject && shownProjects.length > 0)) return null;
          const expandable = isProject || own.length > 0;
          const expanded = expandable && (open.has(config.key) || (searching && (own.length > 0 || isProject)));
          return (
            <div key={config.key}>
              <Row
                icon={config.icon}
                color={config.key}
                label={config.labels.plural}
                expanded={expanded}
                onToggle={expandable ? () => toggle(config.key) : undefined}
                onOpen={() => openTable(config.key)}
              />
              {expanded && own.map((view) => <UserViewRow key={view.id} view={view} level={1} />)}
              {expanded && isProject && (
                <Projects organizationId={organizationId} projects={shownProjects} userViews={byProject} open={open} onToggle={toggle} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
