import { Chip } from "primereact/chip";
import { getEntityType } from "../entities/registry";

export interface IdentifierTypeHeaderProps {
  entityType: string;
  // The JSF class prefix of the entity's chips (`<prefix>-chip-alt`, `<prefix>-type-chip`).
  chipPrefix: string;
  label: string | null | undefined;
  typeLabel?: string | null;
}

/**
 * The fiche header of an entity whose type is edited in the fiche itself (container, phase, find,
 * place): its identifier chip and a read-only type chip — the <x>PanelHeader.xhtml content, without
 * a second edit path to the type.
 */
export function IdentifierTypeHeader({ entityType, chipPrefix, label, typeLabel }: IdentifierTypeHeaderProps) {
  return (
    <div className={`${chipPrefix}-detail-header`} style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}>
      <Chip label={label || ""} className={`${chipPrefix}-chip-alt entity-nav-chip`} icon={getEntityType(entityType)?.icon} />
      {typeLabel && <Chip label={typeLabel} className={`mr-2 ${chipPrefix}-type-chip`} />}
    </div>
  );
}
