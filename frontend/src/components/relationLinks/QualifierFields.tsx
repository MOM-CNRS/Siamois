import { useQuery } from "@tanstack/react-query";
import { Checkbox } from "primereact/checkbox";
import { queryKeys } from "../../api/queryKeys";
import { fetchFieldConceptOptions } from "../../fields/optionSources";
import { t } from "../../i18n";
import { CreateFormField } from "../CreateFormShell";
import { InlinePicker, type PickerOption } from "./InlinePicker";

// Where the types of a stratigraphic link come from: the list of the field that holds the link (its
// field code, or the branch or collection the project restricts it to), narrowed by the unit's type.
export interface TypeVocabulary {
  fieldId: string;
  valueConceptId?: string;
}

export interface Qualifier {
  type: PickerOption | null;
  uncertain: boolean;
}

interface Props {
  vocabulary: TypeVocabulary;
  projectId?: string | number | null;
  organizationId?: number;
  value: Qualifier;
  onChange: (value: Qualifier) => void;
}

// What qualifies a stratigraphic link: its type, picked in the field's list, and whether it is uncertain.
export function QualifierFields({ vocabulary, projectId, organizationId, value, onChange }: Props) {
  const types = useQuery({
    queryKey: queryKeys.stratigraphyTypes(organizationId, vocabulary.fieldId, projectId == null ? null : String(projectId), vocabulary.valueConceptId),
    queryFn: () =>
      fetchFieldConceptOptions(organizationId!, vocabulary.fieldId, projectId == null ? undefined : String(projectId), undefined, {
        valueConceptId: vocabulary.valueConceptId,
      }),
    enabled: organizationId != null,
  });

  const loadTypes = async (query: string): Promise<PickerOption[]> => {
    const q = query.trim().toLowerCase();
    return (types.data ?? []).filter((option) => !q || option.label.toLowerCase().includes(q)).map((option) => ({ id: option.id, label: option.label }));
  };

  return (
    <>
      <CreateFormField label={t("recordingUnit.graph.dialog.type")}>
        <InlinePicker value={value.type} onChange={(type) => onChange({ ...value, type })} load={loadTypes} placeholder={t("recordingUnit.graph.dialog.noType")} />
      </CreateFormField>
      <label className="sia-create-form-field sia-create-form-field-inline">
        <Checkbox checked={value.uncertain} onChange={(e) => onChange({ ...value, uncertain: !!e.checked })} />
        <span>{t("recordingUnit.graph.dialog.uncertain")}</span>
      </label>
    </>
  );
}
