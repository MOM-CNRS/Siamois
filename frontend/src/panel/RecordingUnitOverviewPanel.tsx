import { useEffect, useState } from "react";
import { ProgressSpinner } from "primereact/progressspinner";
import { fetchRecordingUnit, type RecordingUnitResource } from "../api/recordingUnit";
import { fetchFormForType, type RecordingUnitType } from "../api/recordingUnitType";
import { parseLayoutJson } from "../form/schema";
import { useEntityForm } from "../form/useEntityForm";
import { PanelHeader } from "./PanelHeader";
import { DetailsTab } from "./DetailsTab";
import type { PanelActions } from "./panelActions";

export interface RecordingUnitOverviewPanelProps {
  recordingUnitId: number;
  actions?: PanelActions;
  bookmarked: boolean;
}

/**
 * Phase 4 of the migration plan: the full overview panel — toolbar, header and Détails tab — all
 * PrimeReact. Documents/Hierarchy/Mobiliers are deliberately left out entirely for now (phase 5, not
 * even shown as stubs) — Stratigraphy is out of scope for the React panel altogether (stays JSF-only
 * on the main panel, never embedded here — see project_ru_react_migration memory).
 */
export function RecordingUnitOverviewPanel({ recordingUnitId, actions, bookmarked }: RecordingUnitOverviewPanelProps) {
  // Prev/next navigation (in PanelHeader) only changes what THIS component displays — it does not
  // tell FlowBean which unit the overview slot now points at, see panelContent.xhtml's comment.
  const [currentId, setCurrentId] = useState(recordingUnitId);
  const [unit, setUnit] = useState<RecordingUnitResource | null>(null);
  const [formType, setFormType] = useState<RecordingUnitType | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setUnit(null);
    setFormType(null);
    setError(null);

    fetchRecordingUnit(currentId)
      .then(async (ru) => {
        if (cancelled) return;
        setUnit(ru);
        if (ru.type) {
          const type = await fetchFormForType(ru.projectId, ru.type.id);
          if (!cancelled) setFormType(type);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      });

    return () => {
      cancelled = true;
    };
  }, [currentId]);

  if (error) {
    return <div className="ru-panel-error">Erreur de chargement : {error}</div>;
  }
  if (!unit) {
    return (
      <div className="ru-panel-loading" style={{ display: "flex", justifyContent: "center", padding: "2rem" }}>
        <ProgressSpinner style={{ width: "2rem", height: "2rem" }} />
      </div>
    );
  }

  return (
    <RecordingUnitOverviewPanelLoaded
      unit={unit}
      formType={formType}
      onUnitChanged={setUnit}
      onNavigateTo={setCurrentId}
      actions={actions}
      bookmarked={bookmarked}
    />
  );
}

function RecordingUnitOverviewPanelLoaded({
  unit,
  formType,
  onUnitChanged,
  onNavigateTo,
  actions,
  bookmarked,
}: {
  unit: RecordingUnitResource;
  formType: RecordingUnitType | null;
  onUnitChanged: (unit: RecordingUnitResource) => void;
  onNavigateTo: (recordingUnitId: number) => void;
  actions?: PanelActions;
  bookmarked: boolean;
}) {
  const layout = formType ? parseLayoutJson(formType.formBundle.layoutJson) : [];
  const form = useEntityForm({
    recordingUnitId: Number(unit.id),
    layout,
    initialAnswers: unit.answers,
    initialRevision: unit.syncRevision,
  });

  return (
    <div className="ru-overview-panel">
      <PanelHeader
        unit={unit}
        onUnitChanged={onUnitChanged}
        onNavigateTo={onNavigateTo}
        actions={actions}
        bookmarked={bookmarked}
      />

      {form.conflict && (
        <div className="ru-conflict-banner" style={{ background: "var(--yellow-100, #fff3cd)", padding: "0.5rem" }}>
          Cette unité a été modifiée ailleurs entre-temps. Rechargez le panel pour reprendre l'édition.
        </div>
      )}

      <div className="ru-panel-body" style={{ flex: 1, overflow: "auto", padding: "0.5rem" }}>
        {formType ? (
          <DetailsTab layout={layout} formType={formType} projectId={unit.projectId} form={form} />
        ) : (
          <p>Aucun formulaire configuré pour ce type d'unité.</p>
        )}
      </div>
    </div>
  );
}
