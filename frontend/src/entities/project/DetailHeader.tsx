import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { patchProject } from "./api";
import type { ProjectDetail } from "./types";

// actionUnitPanelHeader.xhtml's content (identifier chip + pencil/apply/cancel, then category/
// name/location chips) — lives in EntityDetailPanel's own PrimeReact <Panel> `header`, alongside
// the toolbar in `icons`, exactly matching the real markup's single sideview-titlebar div that
// holds both the toolbar form and this header include side by side. Previously this content was
// rendered inside FicheTab's own header, which was wrong on two counts: it duplicated a titlebar
// the panel already has, and it made this Project-specific content invisible to the (also
// Project-specific but otherwise unrelated) generic toolbar mechanism.
export interface ProjectDetailHeaderProps {
  entity: ProjectDetail;
  onSaved: () => void;
}

export function ProjectDetailHeader({ entity, onSaved }: ProjectDetailHeaderProps) {
  const [identifierEditing, setIdentifierEditing] = useState(false);
  const [identifierDraft, setIdentifierDraft] = useState(entity.fullIdentifier || entity.identifier);
  const [identifierError, setIdentifierError] = useState<string | null>(null);

  const identifierMutation = useMutation({
    mutationFn: (identifier: string) => patchProject(entity.id, { identifier }),
    onSuccess: () => {
      setIdentifierEditing(false);
      setIdentifierError(null);
      onSaved();
    },
    onError: (err: unknown) => {
      setIdentifierError(err instanceof ApiError ? err.message : "Échec de l'enregistrement");
    },
  });

  function saveIdentifier() {
    const trimmed = identifierDraft.trim();
    if (!trimmed) {
      setIdentifierError("L'identifiant est obligatoire");
      return;
    }
    identifierMutation.mutate(trimmed);
  }

  return (
    <div className="project-detail-header" style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}>
      <div className="project-fiche-tab-identifier" style={{ display: "flex", alignItems: "center" }}>
        {identifierEditing ? (
          <>
            <InputText value={identifierDraft} onChange={(e) => setIdentifierDraft(e.target.value)} />
            <Button icon="pi pi-check" onClick={saveIdentifier} loading={identifierMutation.isPending} />
            <Button
              icon="pi pi-times"
              className="p-button-text"
              onClick={() => {
                setIdentifierEditing(false);
                setIdentifierError(null);
                setIdentifierDraft(entity.fullIdentifier || entity.identifier);
              }}
            />
          </>
        ) : (
          <>
            {/* pages/shared/chip/editableIdentifierChip.xhtml's read mode is a plain p:chip */}
            <Chip label={entity.fullIdentifier || entity.identifier} className="entity-nav-chip" />
            <Button icon="pi pi-pencil" className="p-button-text" onClick={() => setIdentifierEditing(true)} />
          </>
        )}
      </div>
      {/* actionUnitPanelHeader.xhtml's categoryChip is a full concept-autocomplete inline editor
          (siaInplace:concept) — no REST autocomplete for that field exists yet (fields/
          renderers.tsx's SELECT_* gap), so this is read-only, same treatment as the type field in
          the schema-driven form. */}
      {entity.type?.resolvedLabel && <Chip label={entity.type.resolvedLabel} className="action-unit-type-chip" />}
      <Chip label={entity.name} className="mr-2 action-unit-type-chip" />
      {entity.mainLocation?.name && (
        <Chip label={entity.mainLocation.name} icon="bi bi-geo-alt" className="mr-2 action-unit-type-chip" />
      )}
      {identifierError && <Message severity="error" text={identifierError} />}
    </div>
  );
}
