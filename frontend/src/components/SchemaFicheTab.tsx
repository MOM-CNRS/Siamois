import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { queryKeys } from "../api/queryKeys";
import { getEffectiveForm } from "../entities/typeCatalog";
import { FormLayoutView } from "../fields/FormLayoutView";
import { parseLayout } from "../fields/layout";
import type { AnswerInputBody, FieldResource } from "../fields/types";
import { useCanEdit } from "../panels/writeMode";
import { FormSkeleton } from "./DetailSkeleton";
import { t } from "../i18n";

/** What the fiche reads off an entity: its project and type, or the form served with it. */
export interface SchemaFicheEntity {
  id?: string | number;
  projectId?: string | number | null;
  type?: { id: string } | null;
  organization?: { id: string } | null;
  formBundle?: { layoutJson: string } | null;
  fields?: Record<string, FieldResource> | null;
  _permissions?: { canEdit?: boolean };
}

export interface SchemaFicheTabProps<TEntity extends SchemaFicheEntity> {
  entity: TEntity;
  entityType: string;
  /**
   * Where the form comes from: the project's types catalog when the form is configured per type
   * (`typesSegment`, e.g. "phase-types" — the entity's type's form, else the untyped one), or the
   * detail response itself (`formBundle`/`fields`) when it's absent.
   */
  typesSegment?: string;
  save: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
  isFieldShown?: (field: FieldResource, stored: unknown) => boolean;
}

/**
 * The fiche of every entity whose form is served by the schema (all but the project): the panels
 * of its layout, each field editable on click (FieldEditCell), gated on write mode and the
 * entity's `_permissions.canEdit`. Each field saves itself through `save`, keyed by field id.
 */
export function SchemaFicheTab<TEntity extends SchemaFicheEntity>({
  entity,
  entityType,
  typesSegment,
  save,
  onSaved,
  isFieldShown,
}: SchemaFicheTabProps<TEntity>) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const perType = typesSegment != null;
  const formQuery = useQuery({
    queryKey: queryKeys.effectiveForm(typesSegment ?? "", entity.projectId, entity.type?.id),
    queryFn: () => getEffectiveForm(typesSegment!, entity.projectId!, entity.type?.id ?? null),
    enabled: perType && entity.projectId != null,
  });

  const form = perType
    ? formQuery.data
    : entity.formBundle
      ? { layoutJson: entity.formBundle.layoutJson, fields: entity.fields ?? {} }
      : undefined;
  const layoutJson = form?.layoutJson;
  const panels = useMemo(() => (layoutJson != null ? parseLayout(layoutJson) : []), [layoutJson]);

  const missingProject = perType && entity.projectId == null;
  const formError = perType ? formQuery.error : !entity.formBundle;

  return (
    <div className="sia-fiche-tab">
      {missingProject && <Message severity="warn" text={t("fiche.unknownProject")} />}
      {perType && formQuery.isLoading && <FormSkeleton />}
      {formError && <Message severity="error" text={t("fiche.formConfigError")} />}
      {form && (
        <FormLayoutView
          entity={entity}
          entityType={entityType}
          fields={form.fields}
          panels={panels}
          canEdit={canEdit}
          organizationId={organizationId}
          onSave={save}
          onSaved={onSaved}
          isFieldShown={isFieldShown}
        />
      )}
    </div>
  );
}
