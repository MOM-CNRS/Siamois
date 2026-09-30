import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Checkbox } from "primereact/checkbox";
import { InputNumber } from "primereact/inputnumber";
import { OverlayPanel } from "primereact/overlaypanel";
import { Skeleton } from "primereact/skeleton";
import { Message } from "primereact/message";
import { getEntityType } from "../entities/registry";
import type { DuplicationConfig, DuplicationResult, DuplicationStructure } from "../entities/types";
import { entityChipStyle } from "../fields/display";
import { CreateFormShell } from "./CreateFormShell";
import { check, createdCount, indexStructure, selectAll, uncheck } from "./duplicateSelection";
import { queryKeys } from "../api/queryKeys";

export interface DuplicateStructureOverlayProps {
  entityType: string;
  // The entity to duplicate; with `anchor` null the overlay is closed.
  entityId: string | number | null;
  // The row action's button the overlay opens next to — like every creation, never a modal.
  anchor: HTMLElement | null;
  onDone: (result: DuplicationResult, source: DuplicationStructure["root"]) => void;
  onHide: () => void;
}

/**
 * JSF's "Dupliquer la structure" dialog as an overlay on the row action: the entity and its
 * descendants as a tree to tick (the entity itself is always copied), the number of exemplars, and
 * what that makes in all.
 */
export function DuplicateStructureOverlay({ entityType, entityId, anchor, onDone, onHide }: DuplicateStructureOverlayProps) {
  const config = getEntityType(entityType);
  const overlayRef = useRef<OverlayPanel>(null);

  useEffect(() => {
    if (anchor) overlayRef.current?.show(null as never, anchor);
    else overlayRef.current?.hide();
  }, [anchor]);

  const duplication = config?.duplication;
  if (!duplication) return null;
  return (
    // Keys typed in the form must not reach whatever hosts it, as in CreateEntityOverlay.
    <div onKeyDown={(e) => e.stopPropagation()} style={{ display: "contents" }}>
      <OverlayPanel
        ref={overlayRef}
        className="entity-list-panel-create-overlay create-entity-overlay"
        onHide={onHide}
        aria-label="Dupliquer la structure"
      >
        {/* Unmounted while closed, so each opening starts from its own fresh tree. */}
        {anchor && entityId != null && (
          <DuplicateForm
            entityType={entityType}
            entityId={entityId}
            duplication={duplication}
            onDone={onDone}
            onCancel={() => overlayRef.current?.hide()}
          />
        )}
      </OverlayPanel>
    </div>
  );
}

function DuplicateForm({
  entityType,
  entityId,
  duplication,
  onDone,
  onCancel,
}: {
  entityType: string;
  entityId: string | number;
  duplication: DuplicationConfig;
  onDone: DuplicateStructureOverlayProps["onDone"];
  onCancel: () => void;
}) {
  const structure = useQuery({
    queryKey: queryKeys.duplicationStructure(entityType, entityId),
    queryFn: () => duplication.load(entityId),
    // What is picked from is the tree as it is now.
    gcTime: 0,
  });
  // Nothing ticked to begin with: copying just the row is the usual case, and opting descendants in
  // is safer than having to opt them out.
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [copies, setCopies] = useState<number>(1);

  const data = structure.data;
  const rootId = data?.root.id;
  const index = useMemo(() => (data ? indexStructure(data.descendants, data.root.id) : null), [data]);
  const depthOf = useMemo(() => {
    const depths = new Map<string, number>();
    if (!data || !index) return depths;
    for (const node of data.descendants) {
      let depth = 1;
      for (let p = index.parentOf.get(String(node.id)); p != null && p !== String(data.root.id); p = index.parentOf.get(p)) depth++;
      depths.set(String(node.id), depth);
    }
    return depths;
  }, [data, index]);

  const mutation = useMutation({
    mutationFn: () =>
      duplication.run(entityId, {
        copies,
        descendantIds: data!.descendants.filter((n) => selected.has(String(n.id))).map((n) => n.id),
      }),
    onSuccess: (result) => onDone(result, data!.root),
  });

  const descendants = data?.descendants ?? [];
  const allSelected = descendants.length > 0 && selected.size >= descendants.length;
  const total = createdCount(selected.size, copies);
  const copiesValid = Number.isInteger(copies) && copies >= 1 && copies <= duplication.maxCopies;
  const error =
    mutation.error instanceof Error
      ? mutation.error.message || "Échec de la duplication"
      : structure.isError
        ? "La structure n'a pas pu être chargée"
        : null;

  function toggle(id: string, on: boolean) {
    if (!index || rootId == null) return;
    setSelected(on ? check(index, selected, rootId, id) : uncheck(index, selected, id));
  }

  const chipStyle = entityChipStyle(entityType);
  const chip = (label: string) => (
    <span className="entity-nav-chip duplicate-structure-chip" style={chipStyle}>
      <span className="entity-nav-chip-label">{label}</span>
    </span>
  );

  return (
    <CreateFormShell
      entityType={entityType}
      title="Dupliquer la structure"
      submitLabel="Dupliquer la structure"
      canSubmit={data != null && copiesValid && !mutation.isPending}
      pending={mutation.isPending}
      error={error}
      footerNote={
        <span role="status">
          {1 + selected.size} {duplication.unit} × {Math.max(1, copies)} = <strong>{total}</strong> nouvelle{total > 1 ? "s" : ""} {duplication.unit}
        </span>
      }
      onSubmit={() => mutation.mutate()}
      onCancel={onCancel}
    >
      <div className="duplicate-structure">
        <label className="duplicate-structure-copies">
          <span className="create-form-label">Nombre de duplicata</span>
          <InputNumber
            value={copies}
            onValueChange={(e) => setCopies(e.value ?? 0)}
            min={1}
            max={duplication.maxCopies}
            inputClassName="duplicate-structure-count-input"
            aria-label="Nombre de duplicata"
          />
        </label>

        <section className="duplicate-structure-elements">
          {descendants.length > 0 && (
            <button
              type="button"
              className="duplicate-structure-toggle-all"
              onClick={() => setSelected(allSelected ? new Set() : selectAll(descendants))}
            >
              {allSelected ? "Tout décocher" : "Tout cocher"}
            </button>
          )}

          {structure.isLoading ? (
            <Skeleton height="6rem" />
          ) : data ? (
            <ul className="duplicate-structure-tree" aria-label="Éléments à dupliquer">
              <li className="duplicate-structure-node">
                {/* The unit itself is always copied: a tick that can't be changed. */}
                <Checkbox inputId="dup-root" checked disabled aria-label="Toujours dupliquée" />
                <label htmlFor="dup-root">{chip(data.root.label)}</label>
              </li>
              {descendants.map((node) => (
                <li key={node.id} className="duplicate-structure-node" style={{ paddingLeft: `${(depthOf.get(String(node.id)) ?? 1) * 1.25}rem` }}>
                  <Checkbox
                    inputId={`dup-${node.id}`}
                    checked={selected.has(String(node.id))}
                    onChange={(e) => toggle(String(node.id), e.checked === true)}
                  />
                  <label htmlFor={`dup-${node.id}`}>{chip(node.label)}</label>
                </li>
              ))}
            </ul>
          ) : null}
          {data?.truncated && (
            <Message severity="warn" text="La structure est trop grande pour être affichée en entier : seuls les premiers éléments sont proposés." />
          )}
        </section>
      </div>
    </CreateFormShell>
  );
}
