import { useState } from "react";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { fetchAdjacent, nextValidationStatus, patchRecordingUnit, type RecordingUnitResource } from "../api/recordingUnit";
import { ApiError } from "../api/client";
import type { PanelActions } from "./panelActions";

export interface PanelHeaderProps {
  unit: RecordingUnitResource;
  /** Called after any header action that changes the unit (identifier edit, validated toggle) — the
   *  parent updates its copy so header + Détails tab both reflect the new revision. */
  onUnitChanged: (unit: RecordingUnitResource) => void;
  onNavigateTo: (recordingUnitId: number) => void;
  /** JSF-bridged toolbar actions — see panelActions.ts. Omitted only in the standalone dev harness. */
  actions?: PanelActions;
  bookmarked: boolean;
}

const VALIDATION_LABEL: Record<RecordingUnitResource["validated"], string> = {
  INCOMPLETE: "Incomplet",
  COMPLETE: "Complet",
  VALIDATED: "Validé",
};
const VALIDATION_ICON: Record<RecordingUnitResource["validated"], string> = {
  INCOMPLETE: "pi pi-circle",
  COMPLETE: "pi pi-circle-fill",
  VALIDATED: "pi pi-check-circle",
};

export function PanelHeader({ unit, onUnitChanged, onNavigateTo, actions, bookmarked }: PanelHeaderProps) {
  const [editingIdentifier, setEditingIdentifier] = useState(false);
  const [identifierDraft, setIdentifierDraft] = useState(unit.fullIdentifier);
  const [identifierError, setIdentifierError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Optimistic: the JSF toggle doesn't report its new state back to this JS call synchronously (see
  // panelActions.ts) — reconciled for real whenever the sideview re-renders and remounts this panel.
  const [bookmarkedLocal, setBookmarkedLocal] = useState(bookmarked);

  async function commitIdentifier() {
    const trimmed = identifierDraft.trim();
    if (!trimmed) {
      setIdentifierError("L'identifiant ne peut pas être vide.");
      return;
    }
    if (trimmed === unit.fullIdentifier) {
      setEditingIdentifier(false);
      return;
    }
    setBusy(true);
    setIdentifierError(null);
    try {
      const updated = await patchRecordingUnit(unit.id, {
        expectedRevision: unit.syncRevision,
        answers: { "7": { value: trimmed } }, // fullIdentifier is system field id 7 (RecordingUnit.java)
      });
      onUnitChanged(updated);
      setEditingIdentifier(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setIdentifierError("Cet identifiant existe déjà dans cette unité d'action.");
      } else {
        setIdentifierError("Échec de l'enregistrement.");
      }
    } finally {
      setBusy(false);
    }
  }

  async function toggleValidated() {
    setBusy(true);
    try {
      const updated = await patchRecordingUnit(unit.id, {
        expectedRevision: unit.syncRevision,
        validated: nextValidationStatus(unit.validated),
      });
      onUnitChanged(updated);
    } finally {
      setBusy(false);
    }
  }

  async function goToAdjacent(direction: "previous" | "next") {
    setBusy(true);
    try {
      const adjacent = await fetchAdjacent(unit.id);
      const targetId = direction === "previous" ? adjacent.previousId : adjacent.nextId;
      if (targetId != null) onNavigateTo(targetId);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="ru-panel-header">
      {actions && (
        <div
          className="ru-panel-toolbar"
          style={{ display: "flex", alignItems: "center", gap: "0.25rem", padding: "0.25rem 0.5rem" }}
        >
          <Button icon="pi pi-angle-double-right" text rounded aria-label="Fermer l'aperçu latéral" onClick={actions.close} />
          <Button
            icon={bookmarkedLocal ? "pi pi-bookmark-fill" : "pi pi-bookmark"}
            text
            rounded
            aria-label="Favori"
            onClick={() => {
              setBookmarkedLocal((v) => !v);
              actions.toggleBookmark();
            }}
          />
          <Button icon="pi pi-copy" text rounded aria-label="Dupliquer" onClick={actions.duplicate} />
          <Button icon="pi pi-refresh" text rounded aria-label="Rafraîchir" onClick={actions.refresh} />
        </div>
      )}

      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flexWrap: "wrap", padding: "0.25rem 0.5rem" }}>
        <Button
          icon={VALIDATION_ICON[unit.validated]}
          label={VALIDATION_LABEL[unit.validated]}
          text
          disabled={busy}
          onClick={() => void toggleValidated()}
        />

        {/* Original JSF layout: previous/next as an up/down pair, side by side, before the identifier. */}
        <div style={{ display: "flex" }}>
          <Button icon="pi pi-chevron-up" text rounded aria-label="Fiche précédente" disabled={busy} onClick={() => void goToAdjacent("previous")} />
          <Button icon="pi pi-chevron-down" text rounded aria-label="Fiche suivante" disabled={busy} onClick={() => void goToAdjacent("next")} />
        </div>

        {editingIdentifier ? (
          <span style={{ display: "flex", flexDirection: "column" }}>
            <InputText
              value={identifierDraft}
              onChange={(e) => setIdentifierDraft(e.target.value)}
              onBlur={() => void commitIdentifier()}
              onKeyDown={(e) => {
                if (e.key === "Enter") void commitIdentifier();
                if (e.key === "Escape") {
                  setIdentifierDraft(unit.fullIdentifier);
                  setEditingIdentifier(false);
                }
              }}
              disabled={busy}
              autoFocus
            />
            {identifierError && <small style={{ color: "var(--red-500, #e24c4c)" }}>{identifierError}</small>}
          </span>
        ) : (
          <span
            className="ru-identifier-chip"
            role="button"
            tabIndex={0}
            onClick={() => setEditingIdentifier(true)}
            style={{ fontWeight: 600, cursor: "text" }}
          >
            {unit.fullIdentifier}
          </span>
        )}

        {unit.type?.resolvedLabel && <span className="ru-type-chip p-tag">{unit.type.resolvedLabel}</span>}
      </div>
    </div>
  );
}
