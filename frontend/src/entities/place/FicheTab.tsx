import { useMemo } from "react";
import { Message } from "primereact/message";
import { useCanEdit } from "../../panels/writeMode";
import { FormLayoutView } from "../../fields/FormLayoutView";
import type { AnswerInputBody } from "../../fields/types";
import { parseLayout } from "../project/form";
import { placePanelLabel } from "./form";
import { patchPlaceAnswers } from "./api";
import type { PlaceDetail } from "./types";

// The fiche for SpatialUnit.DETAILS_FORM — unlike Phase/Container/RecordingUnit/Find, Place has
// no per-type catalog to resolve (SpatialUnit is absent from ConfigurableTable): the layout and
// field catalog already ride along on the detail response itself (entity.formBundle/entity.fields
// — see PlaceOpenApiService#getPlaceById), so there is no separate "effective form" query here,
// unlike PhaseFicheTab/ContainerFicheTab.
//
// EDIT MODEL: identical to the other fiches' — no Modifier/Enregistrer/Annuler, fields edit on
// click via FieldEditCell/CellEditOverlay, gated on WriteModeProvider AND `_permissions.canEdit`.
// `patchPlaceAnswers` is an adapter (see api.ts's own doc): PlacePatchRequest has no generic
// `answers` map, so it translates the field-id-keyed answers this fiche produces into that flat
// shape.
//
// The address field (CustomFieldSelectOneAddress, valueBinding "address") is deliberately
// skipped, not rendered: FullAddress is a composite object with no simple editor in this
// architecture yet, and buildPlaceAnswers never puts an entry for it in `answers`, so
// resolveValueBinding would otherwise read a raw FullAddress object into a text/concept renderer
// that can't represent it. A known, documented gap (see PlaceOpenApiService#getPlaceById's own
// javadoc), not an oversight.
interface PlaceFicheTabProps {
  entity: PlaceDetail;
  onSaved: () => void;
}

export function PlaceFicheTab({ entity, onSaved }: PlaceFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const fields = entity.fields;
  const panels = useMemo(
    () => (entity.formBundle ? parseLayout(entity.formBundle.layoutJson) : []),
    [entity.formBundle],
  );

  const save = (id: string | number, answers: Record<string, AnswerInputBody>) =>
    patchPlaceAnswers(id, answers);

  return (
    <div className="place-fiche-tab sia-fiche-tab">
      {!entity.formBundle && (
        <Message severity="error" text="Impossible de charger la configuration du formulaire" />
      )}
      {fields && (
        <FormLayoutView
          entity={entity}
          entityType="place"
          fields={fields}
          panels={panels}
          panelLabel={placePanelLabel}
          canEdit={canEdit}
          organizationId={organizationId}
          onSave={save}
          onSaved={onSaved}
          // The address has its own block in the place header.
          isFieldShown={(field) => field.valueBinding !== "address"}
        />
      )}
    </div>
  );
}
