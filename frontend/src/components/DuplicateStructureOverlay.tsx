import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Checkbox } from "primereact/checkbox";
import { InputNumber } from "primereact/inputnumber";
import { Skeleton } from "primereact/skeleton";
import { Message } from "primereact/message";
import { getEntityType } from "../entities/registry";
import type { DuplicationConfig, DuplicationResult, DuplicationStructure } from "../entities/types";
import { entityChipStyle } from "../fields/display";
import { AnchoredFormOverlay } from "./AnchoredFormOverlay";
import { CreateFormShell } from "./CreateFormShell";
import { check, createdCount, indexStructure, selectAll, uncheck } from "./duplicateSelection";
import { queryKeys } from "../api/queryKeys";
import { messageForError } from "../api/errors";
import { t, tn } from "../i18n";

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
  const duplication = getEntityType(entityType)?.duplication;
  if (!duplication) return null;
  return (
    <AnchoredFormOverlay anchor={anchor} ariaLabel={t("dup.title")} onHide={onHide}>
      {(close) =>
        entityId != null && (
          <DuplicateForm entityType={entityType} entityId={entityId} duplication={duplication} onDone={onDone} onCancel={close} />
        )
      }
    </AnchoredFormOverlay>
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
    mutation.error != null
      ? messageForError(mutation.error, t("dup.failed"))
      : structure.isError
        ? messageForError(structure.error, t("dup.loadFailed"))
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
      title={t("dup.title")}
      submitLabel={t("dup.title")}
      canSubmit={data != null && copiesValid && !mutation.isPending}
      pending={mutation.isPending}
      error={error}
      footerNote={
        <span role="status">
          {t("dup.summaryPrefix", { selected: 1 + selected.size, unit: duplication.unit, copies: Math.max(1, copies) })}
          <strong>{total}</strong>
          {tn("dup.summarySuffix", total, { unit: duplication.unit })}
        </span>
      }
      onSubmit={() => mutation.mutate()}
      onCancel={onCancel}
    >
      <div className="duplicate-structure">
        <label className="duplicate-structure-copies">
          <span className="sia-create-form-label">{t("dup.copies")}</span>
          <InputNumber
            value={copies}
            onValueChange={(e) => setCopies(e.value ?? 0)}
            min={1}
            max={duplication.maxCopies}
            inputClassName="duplicate-structure-count-input"
            aria-label={t("dup.copies")}
          />
        </label>

        <section className="duplicate-structure-elements">
          {descendants.length > 0 && (
            <button
              type="button"
              className="duplicate-structure-toggle-all"
              onClick={() => setSelected(allSelected ? new Set() : selectAll(descendants))}
            >
              {allSelected ? t("dup.uncheckAll") : t("dup.checkAll")}
            </button>
          )}

          {structure.isLoading ? (
            <Skeleton height="6rem" />
          ) : data ? (
            <ul className="duplicate-structure-tree" aria-label={t("dup.elements")}>
              <li className="duplicate-structure-node">
                {/* The unit itself is always copied: a tick that can't be changed. */}
                <Checkbox inputId="dup-root" checked disabled aria-label={t("dup.alwaysCopied")} />
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
            <Message severity="warn" text={t("dup.truncated")} />
          )}
        </section>
      </div>
    </CreateFormShell>
  );
}
