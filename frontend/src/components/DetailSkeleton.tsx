import { Skeleton } from "primereact/skeleton";

/**
 * Loading placeholders for a fiche. Wrapped in .loading-skeleton, which fades them in only after a
 * short delay (main-panel.css): a fast response never shows them, so nothing flashes; a slow one
 * shows the page's shape instead of a blank box.
 */

// The fiche form: a couple of panels of label/value pairs, on the same grid as the real one.
export function FormSkeleton({ panels = 2, fieldsPerPanel = 6 }: { panels?: number; fieldsPerPanel?: number }) {
  return (
    <div className="loading-skeleton form-skeleton" aria-busy="true" aria-label="Chargement…">
      {Array.from({ length: panels }, (_, p) => (
        <div key={p} className="form-skeleton-panel">
          <Skeleton width="10rem" height="1.25rem" className="form-skeleton-title" />
          <div className="project-fiche-tab-row">
            {Array.from({ length: fieldsPerPanel }, (_, f) => (
              <div key={f} className="project-fiche-tab-col ui-g-12 ui-md-6 ui-lg-4">
                <Skeleton width="40%" height="0.8rem" className="form-skeleton-label" />
                <Skeleton width="85%" height="1.6rem" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

// Everything under a fiche's header while the entity loads: breadcrumb, tab strip, then the form.
export function DetailBodySkeleton() {
  return (
    <div className="loading-skeleton sia-detail-skeleton" aria-busy="true" aria-label="Chargement…">
      <Skeleton width="12rem" height="1rem" className="sia-detail-skeleton-breadcrumb" />
      <div className="sia-detail-skeleton-tabs">
        <Skeleton width="6rem" height="1.5rem" />
        <Skeleton width="9rem" height="1.5rem" />
        <Skeleton width="7rem" height="1.5rem" />
      </div>
      <FormSkeleton />
    </div>
  );
}
