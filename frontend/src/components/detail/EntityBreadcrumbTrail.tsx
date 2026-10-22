import { useRef } from "react";
import { BreadCrumb } from "primereact/breadcrumb";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import type { MenuItem, MenuItemOptions } from "primereact/menuitem";
import { getEntityType } from "../../entities/registry";
import type { EntityBreadcrumb, EntityBreadcrumbs } from "../../entities/shared/types";
import { t } from "../../i18n";

export interface EntityBreadcrumbTrailProps {
  // createHomeItem: icon only, with a real redirect to the dashboard.
  home: MenuItem;
  // Home + « Tous les … » only: shown for an entity with no breadcrumbs, and while they load.
  fallback: MenuItem[];
  // The fiche's paths from its project; the first is shown, all of them feed the overlay.
  breadcrumbs?: EntityBreadcrumbs;
  onNavigate?: (entityType: string, id?: string | number) => void;
}

// The trail stops at the fiche's parent: the fiche itself is the one on screen, so its own crumb
// (the last of a path) is left out. Every crumb shown is a link.
// A long identifier or name would push the rest of the trail out of the pane: cut it, the full text
// stays in the link's tooltip.
export const MAX_CRUMB_LENGTH = 24;

function truncate(label: string): string {
  return label.length > MAX_CRUMB_LENGTH ? `${label.slice(0, MAX_CRUMB_LENGTH - 1).trimEnd()}…` : label;
}

function toItems(path: EntityBreadcrumb[], onNavigate: EntityBreadcrumbTrailProps["onNavigate"]): MenuItem[] {
  return path.slice(0, -1).map((crumb) => {
    const icon = getEntityType(crumb.type)?.icon;
    return {
      label: crumb.label,
      command: onNavigate ? () => onNavigate(crumb.type, crumb.id) : undefined,
      // PrimeReact only draws `icon` on the home crumb, so the type's icon goes in a template.
      template: (_item: MenuItem, options: MenuItemOptions) => (
        <a href="#" className={options.className} onClick={options.onClick} title={crumb.label}>
          {icon ? <span className={`p-menuitem-icon ${icon}`} /> : null}
          <span className={options.labelClassName}>{truncate(crumb.label)}</span>
        </a>
      ),
    };
  });
}

/**
 * The fiche's fil d'Ariane: home → project → parents → fiche, following the first path. When the
 * fiche can be reached through several paths, a button in front of the trail opens an overlay
 * listing all of them, each one clickable too.
 */
export function EntityBreadcrumbTrail({ home, fallback, breadcrumbs, onNavigate }: EntityBreadcrumbTrailProps) {
  const overlay = useRef<OverlayPanel>(null);
  const paths = breadcrumbs?.paths ?? [];

  if (paths.length === 0) {
    return <BreadCrumb className="panel-bc" home={home} model={fallback} />;
  }

  return (
    <div className="panel-bc-row">
      {paths.length > 1 ? (
        <>
          <Button
            icon={
              <svg className="panel-bc-paths-chevron" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M3 6l5 5 5-5" />
              </svg>
            }
            className="panel-bc-paths-button"
            text
            rounded
            aria-label={t("breadcrumb.allPaths")}
            tooltip={t("breadcrumb.allPaths")}
            tooltipOptions={{ position: "bottom" }}
            onClick={(event) => overlay.current?.toggle(event)}
          />
          <OverlayPanel ref={overlay} className="panel-bc-paths-overlay">
            {paths.map((path) => (
              <BreadCrumb
                key={path.map((c) => `${c.type}:${c.id}`).join(">")}
                className="panel-bc"
                home={home}
                model={toItems(path, (type, id) => {
                  overlay.current?.hide();
                  onNavigate?.(type, id);
                })}
              />
            ))}
            {breadcrumbs?.truncated ? <div className="panel-bc-paths-truncated">{t("breadcrumb.truncated")}</div> : null}
          </OverlayPanel>
        </>
      ) : null}
      <BreadCrumb className="panel-bc" home={home} model={toItems(paths[0], onNavigate)} />
    </div>
  );
}
