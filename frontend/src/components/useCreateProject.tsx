import { useRef, useState, type ReactNode } from "react";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { searchCreatableProjects, type CreatableKind } from "../entities/project/api";
import type { ProjectSummary } from "../entities/project/types";
import { scopeProjectId } from "../entities/scope";
import type { ListScope } from "../entities/types";

export interface CreateProject {
  // The project the entity will be created in: the list's own when it has one, else the picked one.
  projectId?: string;
  // The project field to render first in the form — null when the list already fixes the project.
  picker: ReactNode;
}

/**
 * The project a create form works in (lot 7). A project-scoped list (a project's tab, a recording
 * unit's children…) fixes it; an organization-wide list lets the user pick it here, among the
 * projects where they may create `kind` (GET /projects?canCreate=…) — everything else in the form
 * depends on it (types catalog, recording units), so the form keeps those disabled until then.
 */
export function useCreateProject({
  scope,
  organizationId,
  kind,
}: {
  scope?: ListScope;
  organizationId?: number;
  kind: CreatableKind;
}): CreateProject {
  const fixedProjectId = scopeProjectId(scope);
  const [picked, setPicked] = useState<ProjectSummary | null>(null);
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<ProjectSummary[]>([]);
  const autoCompleteRef = useRef<AutoComplete>(null);

  if (fixedProjectId != null || organizationId == null) {
    return { projectId: fixedProjectId, picker: null };
  }

  async function search(e: AutoCompleteCompleteEvent) {
    const result = await searchCreatableProjects(organizationId!, kind, e.query || undefined);
    setSuggestions(result.data);
  }

  const picker = (
    <label className="project-create-form-field">
      <span>Projet</span>
      <AutoComplete
        ref={autoCompleteRef}
        value={query}
        suggestions={suggestions}
        field="fullIdentifier"
        itemTemplate={(project: ProjectSummary) => (
          <span>
            {project.fullIdentifier}
            {project.name ? ` — ${project.name}` : ""}
          </span>
        )}
        onChange={(e) => {
          // A free-typed string clears the choice; picking a suggestion sets both.
          if (typeof e.value === "string") {
            setQuery(e.value);
            setPicked(null);
          } else {
            const project = e.value as ProjectSummary;
            setPicked(project);
            setQuery(project.fullIdentifier ?? "");
          }
        }}
        completeMethod={search}
        onFocus={(e) => autoCompleteRef.current?.search(e, e.currentTarget.value ?? "", "dropdown")}
        placeholder="Rechercher un projet…"
        autoFocus
      />
    </label>
  );

  return { projectId: picked ? String(picked.id) : undefined, picker };
}
