/** The project a fiche belongs to, as the server names it (history, bookmarks, search results). */
export interface ProjectRef {
  id: number;
  name: string;
}

/**
 * The name of the project, as small secondary text, for a fiche shown outside its project (history, bookmarks,
 * search): `US 1012` alone does not say which project's. Nothing for a page with no project.
 * The server reads the name each time the list is, so a renamed project shows its new name.
 */
export function ProjectName({ project }: Readonly<{ project?: ProjectRef | null }>) {
  if (!project?.name) return null;
  return (
    <span className="sia-project-name" title={project.name}>
      {project.name}
    </span>
  );
}
