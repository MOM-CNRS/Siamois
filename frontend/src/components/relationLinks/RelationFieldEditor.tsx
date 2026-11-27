import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../api/queryKeys";
import {getRelationField, RELATION_COLLECTIONS} from "./api";
import {RelationLinkEditor} from "./RelationLinkEditor";

// The kinds a relation with no accepted kind can link: every record of a project but the project itself.
const ANY_RECORD = ["recording-units", "finds", "phases", "containers", "documents", "studies"];

export interface RelationFieldEditorProps {
  fieldId: string;
  // Registry key of the edited record ("recordingUnit", "find"…).
  entityType?: string;
  recordId: string | number;
  projectId?: string | null;
  organizationId?: number;
  typeConceptId?: string;
  onChanged: () => void;
  onClose: () => void;
}

/**
 * A configurable relation field (answerType RELATION) on a record: what the field links in the record's
 * project (GET /relation-fields/{fieldId}) decides the pickers, then RelationLinkEditor edits its links.
 */
export function RelationFieldEditor({ fieldId, entityType, recordId, projectId, ...props }: Readonly<RelationFieldEditorProps>) {
  const recordCollection = entityType ? RELATION_COLLECTIONS[entityType] : undefined;
  const description = useQuery({
    queryKey: queryKeys.relationField(fieldId, projectId ?? ""),
    queryFn: () => getRelationField(fieldId, projectId!),
    enabled: projectId != null,
  });
  if (!recordCollection || projectId == null || !description.data) return null;
  const accepted = [...new Set(description.data.accepts.map((end) => end.resourceType))];
  return (
    <RelationLinkEditor
      {...props}
      fieldId={fieldId}
      projectId={projectId}
      recordCollection={recordCollection}
      recordId={recordId}
      accepts={accepted.length > 0 ? accepted : ANY_RECORD}
      singleChoice={description.data.singleChoice}
    />
  );
}
