import { t } from "../i18n";
import type { FilterOption } from "./optionSources";

/**
 * A place suggestion: the database or globe icon (a place of the organization, or one still to be
 * created from an external source), the name, its type, its code and the source it comes from (the
 * JSF place item). Options with no place info render as their bare label.
 */
export function PlaceOptionItem({ option }: Readonly<{ option: FilterOption }>) {
  const place = option.place;
  if (!place) return <>{option.label}</>;
  const external = place.external != null;
  return (
    <div className="place-item">
      <i
        className={`place-item-icon ${external ? "bi bi-globe2" : "bi bi-database"}`}
        title={t(external ? "place.suggestion.external" : "place.suggestion.internal")}
      />
      <span className="place-item-name">{option.label}</span>
      {place.category && <span className="place-item-chip">{place.category}</span>}
      {place.code && <small className="place-item-code">{place.code}</small>}
      <span className="place-item-chip place-item-source">{place.source}</span>
    </div>
  );
}
