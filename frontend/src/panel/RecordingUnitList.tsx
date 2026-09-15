import { useEffect, useState } from "react";
import { DataTable, type DataTableStateEvent } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { listRecordingUnits, patchRecordingUnit, type RecordingUnitResource } from "../api/recordingUnit";
import { getFieldRenderer } from "../form/fieldRegistry";
import { toAnswerInput, toLocalValue, type LocalValue } from "../form/answerCodec";
import type { FieldAnswer, ResourceRef } from "../form/schema";

export interface RecordingUnitListProps {
  organizationId: string | number;
  /** Opens `recordingUnitId` as the overview slot next to this list — see focus.xhtml's
   *  ruOpenOverviewMain remoteCommand bridge. Omitted only in the standalone dev harness. */
  onRowOpen?: (recordingUnitId: number) => void;
}

const PAGE_SIZE = 20;

/**
 * Every column RecordingUnitTableDefinitionFactory declares (server-side), minus the ones list rows
 * don't hydrate (parents/children/phases full lists — see RecordingUnitOpenApiService
 * .buildListColumnAnswers, which fills `answers` with exactly this set of valueBindings). Order mirrors
 * the JSF table's declaration order.
 */
const COLUMNS: { binding: string; header: string }[] = [
  { binding: "type", header: "Type" },
  { binding: "actionUnit", header: "Unité d'action" },
  { binding: "spatialUnit", header: "Unité spatiale" },
  { binding: "author", header: "Auteur" },
  { binding: "matrixColor", header: "Couleur matrice" },
  { binding: "openingDate", header: "Date d'ouverture" },
  { binding: "closingDate", header: "Date de fermeture" },
  { binding: "geomorphologicalCycle", header: "Nature" },
  { binding: "geomorphologicalAgent", header: "Agent" },
  { binding: "normalizedInterpretation", header: "Interprétation" },
  { binding: "tpq", header: "TPQ" },
  { binding: "taq", header: "TAQ" },
  { binding: "chronologicalPhase", header: "Phase chronologique" },
  { binding: "erosionShape", header: "Forme d'érosion" },
  { binding: "erosionProfile", header: "Profil d'érosion" },
  { binding: "erosionOrientation", header: "Orientation d'érosion" },
  { binding: "description", header: "Description" },
  { binding: "comments", header: "Commentaires" },
  { binding: "zInf", header: "Z inf." },
  { binding: "zSup", header: "Z sup." },
  { binding: "contributors", header: "Contributeurs" },
];

function answerFor(row: RecordingUnitResource, binding: string): FieldAnswer | undefined {
  return Object.values(row.answers).find((a) => a.field.valueBinding === binding);
}

/**
 * A table cell for one field — always rendered as its editable input (like the JSF table's
 * FormFieldColumns, which pass allowEdit straight through with no separate "start edit" step; only the
 * identifier chip has its own click-to-navigate + pencil-to-edit UX, kept separate below). Buffers
 * keystrokes locally and commits on blur, so typing doesn't PATCH on every character. Columns whose
 * answerType has no wired renderer (action unit, spatial unit, author, contributors, measurements — see
 * fieldRegistry.tsx's UnsupportedField) render read-only automatically, same as the JSF table's own
 * readOnly columns.
 */
function EditableCell({
  answer,
  projectId,
  onCommit,
}: {
  answer: FieldAnswer;
  projectId: string;
  onCommit: (value: LocalValue) => void;
}) {
  const [local, setLocal] = useState<LocalValue>(() => toLocalValue(answer));
  useEffect(() => setLocal(toLocalValue(answer)), [answer]);
  const Renderer = getFieldRenderer(answer.answerType);
  return (
    <div onClick={(e) => e.stopPropagation()} onBlur={() => onCommit(local)}>
      <Renderer field={answer.field} value={local} onChange={setLocal} disabled={false} projectId={projectId} />
    </div>
  );
}

/**
 * Full JSF column set (values only, no per-column filters), fields always editable — no row/pencil
 * toggle, matching the JSF table's own always-on inline editing (gated by write permission there, not
 * by a UI mode switch). Server-side paginated, search by identifier + basic sort.
 */
export function RecordingUnitList({ organizationId, onRowOpen }: RecordingUnitListProps) {
  const [rows, setRows] = useState<RecordingUnitResource[]>([]);
  const [total, setTotal] = useState(0);
  const [first, setFirst] = useState(0);
  const [sortField, setSortField] = useState<string>("creationTime");
  const [sortOrder, setSortOrder] = useState<1 | -1>(-1);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    listRecordingUnits({
      organizationId,
      offset: first,
      limit: PAGE_SIZE,
      q: search.trim() || undefined,
      sort: `${sortField}:${sortOrder === 1 ? "asc" : "desc"}`,
    })
      .then((res) => {
        if (cancelled) return;
        setRows(res.data);
        setTotal(res.meta.total);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [organizationId, first, sortField, sortOrder, search]);

  function onPage(e: DataTableStateEvent) {
    setFirst(e.first);
  }

  function onSort(e: DataTableStateEvent) {
    setSortField(e.sortField);
    setSortOrder(e.sortOrder === 1 ? 1 : -1);
    setFirst(0);
  }

  async function commitField(row: RecordingUnitResource, binding: string, value: LocalValue) {
    const answer = answerFor(row, binding);
    if (!answer) return;
    if (JSON.stringify(toLocalValue(answer)) === JSON.stringify(value)) return;

    setSaveError(null);
    try {
      const updated = await patchRecordingUnit(row.id, {
        expectedRevision: row.syncRevision,
        answers: { [answer.field.id]: toAnswerInput(value, answer.answerType) },
      });
      setRows((prev) =>
        prev.map((r) => {
          if (r.id !== row.id) return r;
          const newAnswer: FieldAnswer =
            "values" in answer
              ? ({ ...answer, values: (value as ResourceRef[]) ?? [] } as FieldAnswer)
              : ({ ...answer, value } as FieldAnswer);
          return {
            ...r,
            syncRevision: updated.syncRevision,
            answers: { ...r.answers, [answer.field.id]: newAnswer },
          };
        }),
      );
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : String(err));
    }
  }

  if (error) {
    return <div className="ru-panel-error">Erreur de chargement : {error}</div>;
  }

  return (
    <div className="ru-list-panel" style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ padding: "0.75rem", display: "flex", gap: "0.5rem", alignItems: "center" }}>
        <InputText
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setFirst(0);
          }}
          placeholder="Rechercher par identifiant…"
          style={{ minWidth: "20rem" }}
        />
        <span style={{ color: "var(--text-color-secondary, #6c757d)" }}>{total} unité(s)</span>
        {saveError && <span style={{ color: "var(--red-500, #e24c4c)" }}>Échec de l'enregistrement : {saveError}</span>}
      </div>

      <div style={{ flex: 1, overflow: "hidden" }}>
        <DataTable
          value={rows}
          lazy
          paginator
          rows={PAGE_SIZE}
          first={first}
          totalRecords={total}
          onPage={onPage}
          sortField={sortField}
          sortOrder={sortOrder}
          onSort={onSort}
          loading={loading}
          onRowClick={(e) => {
            const unit = e.data as RecordingUnitResource;
            onRowOpen?.(Number(unit.id));
          }}
          rowHover
          scrollable
          scrollHeight="flex"
          emptyMessage="Aucune unité d'enregistrement."
        >
          <Column
            field="fullIdentifier"
            header="Identifiant"
            sortable
            frozen
            style={{ cursor: onRowOpen ? "pointer" : undefined, fontWeight: 600 }}
          />

          {COLUMNS.map(({ binding, header }) => (
            <Column
              key={binding}
              header={header}
              style={{ minWidth: "10em" }}
              body={(row: RecordingUnitResource) => {
                const answer = answerFor(row, binding);
                if (!answer) return "—";
                return (
                  <EditableCell
                    answer={answer}
                    projectId={row.projectId}
                    onCommit={(value) => void commitField(row, binding, value)}
                  />
                );
              }}
            />
          ))}

          <Column header="Parents" body={(row: RecordingUnitResource) => row._counts?.parents ?? 0} />
          <Column header="Enfants" body={(row: RecordingUnitResource) => row._counts?.children ?? 0} />
          <Column header="Mobiliers" body={(row: RecordingUnitResource) => row._counts?.finds ?? 0} />
        </DataTable>
      </div>
    </div>
  );
}
