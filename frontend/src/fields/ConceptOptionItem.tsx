import { useState, type CSSProperties, type MouseEvent } from "react";
import type { AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { t } from "../i18n";
import type { FilterOption } from "./optionSources";

/**
 * A concept suggestion: label, the preferred label it stands for when it is an alternative one,
 * the (truncated) definition, the parents, and a link to the concept in the thesaurus (the JSF
 * concept item). Other options render as their bare label.
 */
export function ConceptOptionItem({ option }: Readonly<{ option: FilterOption }>) {
  const info = option.concept;
  if (!info) return <>{option.label}</>;

  // The link must not pick the suggestion: stop the press before the list sees it.
  const stop = (e: MouseEvent) => e.stopPropagation();

  return (
    <div className="concept-item">
      {info.thesaurusUrl && (
        <a
          className="concept-item-link"
          href={info.thesaurusUrl}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={t("concept.openInThesaurus")}
          title={t("concept.openInThesaurus")}
          onMouseDown={stop}
          onClick={stop}
        >
          <i className="bi bi-box-arrow-up-right" />
        </a>
      )}
      <div className="concept-item-body">
        <span>{option.label}</span>
        {info.prefLabel && <small className="concept-item-altlabel">{t("concept.altLabelOf", { label: info.prefLabel })}</small>}
        {info.definition && (
          <small className="concept-item-definition" title={info.definition}>
            {info.definition}
          </small>
        )}
        {info.parents && <small>{t("concept.parents", { parents: info.parents })}</small>}
      </div>
    </div>
  );
}

// How wide a suggestion panel may grow past its field.
const PANEL_MAX_WIDTH = 640;

/**
 * Sizes a suggestion panel against its field. The panel is as wide as its widest item, so a long
 * definition would make it span the whole viewport. It may grow up to PANEL_MAX_WIDTH (never below
 * the field's own width): it starts at the field's left edge, and PrimeReact only slides it left
 * when the field is too close to the viewport's right edge. Past the cap the definition
 * ellipsises. Call `capTo` from completeMethod (before the panel opens) and pass `panelStyle` to
 * the AutoComplete.
 */
export function useFieldWidthCap() {
  const [width, setWidth] = useState<number>();
  const capTo = (e: AutoCompleteCompleteEvent) => {
    const field = (e.originalEvent?.target as HTMLElement | null)?.closest<HTMLElement>(".p-autocomplete");
    if (field) setWidth(Math.max(field.offsetWidth, PANEL_MAX_WIDTH));
  };
  const panelStyle: CSSProperties | undefined = width ? { maxWidth: width } : undefined;
  return { capTo, panelStyle };
}
