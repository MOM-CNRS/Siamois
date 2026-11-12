import {ProgressBar} from "primereact/progressbar";

/**
 * The progress of a creation that takes a while (a project configured from its template), shown under
 * the fields of the create form while it runs: what is going on, and how far it is.
 */
export function CreateProgress({ label, percentage }: { label: string; percentage: number }) {
  return (
    <div className="sia-create-form-progress" role="status">
      <small className="sia-create-form-hint">{label}</small>
      <ProgressBar value={percentage} />
    </div>
  );
}
