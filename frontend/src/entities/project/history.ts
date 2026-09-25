import { apiFetch } from "../../api/client";

// GET /api/v1/projects/{id}/history (plan §5/§8 phase 6) — backs the fiche header's "last
// modified" line, not a separate tab/table: JSF's own equivalent (versionTab.xhtml) is a stubbed,
// unused restore UI for ActionUnit (ActionUnitPanel.visualise() is a no-op), so this only needs
// the most recent entry, not the full list rendered anywhere.
export interface ProjectHistoryAuthor {
  id: number | null;
  name: string | null;
  lastname: string | null;
}

export interface ProjectHistoryEntry {
  revisionNumber: number;
  revisionDate: string;
  revisionType: string;
  author: ProjectHistoryAuthor | null;
}

interface ProjectHistoryListResponseBody {
  data: ProjectHistoryEntry[];
}

export async function getProjectHistory(id: string | number): Promise<ProjectHistoryEntry[]> {
  const body = await apiFetch<ProjectHistoryListResponseBody>(`/api/v1/projects/${id}/history`);
  return body.data;
}
