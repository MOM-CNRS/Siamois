import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Chip } from "primereact/chip";
import { EntityDetailHeader } from "../../components/EntityDetailHeader";
import type { FieldResource } from "../../fields/types";
import { patchProject, type ProjectPatch } from "./api";
import { getProjectTypes } from "./projectTypes";
import type { ProjectDetail } from "./types";
import { queryKeys } from "../../api/queryKeys";
import { t } from "../../i18n";

// actionUnitPanelHeader.xhtml's content on the shared fiche header: the name as the main chip, the
// identifier and the type as secondary chips, the location read-only after them. The identifier,
// the name and the type are written through ProjectPatchRequest's flat `name` / `identifier` /
// `typeId`, in one request.
//
// prev/next ("fiche précédente/suivante") is NOT rendered here — it's generic, so it's rendered by
// EntityDetailPanel itself (see EntityDetailPanel.tsx / entities/project/api.ts#getProjectSiblings).
export interface ProjectDetailHeaderProps {
  entity: ProjectDetail;
  onSaved: () => void;
}

export function ProjectDetailHeader({ entity, onSaved }: ProjectDetailHeaderProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;

  // ActionUnitForm.ACTION_UNIT_TYPE_FIELD, from the org catalog the fiche loads (same query key).
  const typesQuery = useQuery({
    queryKey: queryKeys.projectTypes(organizationIdRaw),
    queryFn: () => getProjectTypes(organizationIdRaw as string),
    enabled: organizationIdRaw != null,
  });
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  return (
    <EntityDetailHeader
      entityType="project"
      chipPrefix="action-unit"
      entity={entity}
      primary={{ value: entity.name, label: t("common.name"), requiredMessage: t("header.nameRequired") }}
      secondary={{
        value: entity.fullIdentifier || entity.identifier,
        label: t("common.identifier"),
        requiredMessage: t("header.identifierRequired"),
      }}
      type={{ value: entity.type, field: typeField, organizationId }}
      extra={
        entity.mainLocation?.name && (
          <Chip label={entity.mainLocation.name} icon="bi bi-geo-alt" className="mr-2 action-unit-type-chip" />
        )
      }
      save={(changes) => {
        const patch: ProjectPatch = {};
        if (changes.primary !== undefined) patch.name = changes.primary;
        if (changes.secondary !== undefined) patch.identifier = changes.secondary;
        if (changes.typeId !== undefined) patch.typeId = changes.typeId;
        return patchProject(entity.id, patch);
      }}
      onSaved={onSaved}
    />
  );
}
