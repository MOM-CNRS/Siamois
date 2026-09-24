import type { ColumnDef } from "./types";
import type { ProjectRef } from "./project/types";

// The "Projet" column shared by every organization-wide list whose rows belong to a project
// (UE, mobilier, phases, contenants). Unscoped only: inside a project's own tab every row would
// repeat that same project. Clicking it opens the project's overview, like the identifier chip.
export function projectColumn<T extends { project?: ProjectRef | null }>(): ColumnDef<T> {
  return {
    key: "project",
    header: "Projet",
    render: (row) => row.project?.label ?? "",
    link: (row) => (row.project ? { entityType: "project", id: row.project.resourceId } : null),
    unscopedOnly: true,
  };
}
