import {RelationLinkEditor} from "./RelationLinkEditor";

export interface StratigraphyEditorProps {
  // The field that holds this side of the stratigraphy: its values are the chips, its list gives the types.
  fieldId: string;
  unitId: string | number;
  projectId?: string | null;
  organizationId?: number;
  // The unit's type, which can narrow the field's list of relationship types.
  typeConceptId?: string;
  // After a link changed: whoever shows the relations is stale.
  onChanged: () => void;
  // A chip opened a fiche: the overlay's job is done.
  onClose: () => void;
}

/** One side of a unit's stratigraphy: a relation field between units (RelationLinkEditor). */
export function StratigraphyEditor({ unitId, ...props }: Readonly<StratigraphyEditorProps>) {
  return <RelationLinkEditor {...props} recordCollection="recording-units" recordId={unitId} accepts={["recording-units"]} />;
}
