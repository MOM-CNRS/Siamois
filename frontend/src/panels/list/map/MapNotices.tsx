import {Message} from "primereact/message";
import {t} from "../../../i18n";
import type {MapSummary} from "./mapSummary";

/** The banners above a map: rows that could not be placed, a truncated result set, an empty one. */
export function MapNotices({ summary }: Readonly<{ summary: MapSummary }>) {
  return (
    <>
      {summary.withoutGeometry > 0 && (
        <Message severity="info" className="entity-map-notice" text={t("list.mapWithoutGeometry", { count: summary.withoutGeometry })} />
      )}
      {summary.truncated && (
        <Message severity="warn" className="entity-map-notice" text={t("list.mapTruncated", { shown: summary.examined, total: summary.total })} />
      )}
      {summary.empty && <Message severity="info" className="entity-map-notice" text={t("list.mapEmpty")} />}
    </>
  );
}
