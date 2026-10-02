import { useEffect, useRef, useState } from "react";
import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { messageForError } from "../api/errors";
import { fetchDocumentFile, removeDocumentFile, uploadDocumentFile } from "../entities/document/api";
import { t } from "../i18n";

/** What the server shows of a document's stored file (DocumentAnswersProjector's FILE value). */
export interface StoredFile {
  fileName?: string | null;
  mimeType?: string | null;
  size?: number | null;
}

export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// A larger file is not fetched just to be shown: the download stays one click away.
const INLINE_PREVIEW_MAX_BYTES = 10 * 1024 * 1024;

const previewKind = (mimeType?: string | null): "image" | "pdf" | null =>
  mimeType?.startsWith("image/") ? "image" : mimeType === "application/pdf" ? "pdf" : null;

export interface FileCellProps {
  documentId: string | number;
  file: StoredFile | null;
  // The user may send, replace and remove the file (the document's edit right).
  editable: boolean;
  // Called after a successful upload or removal, so the fiche refetches.
  onChanged: () => void;
}

/**
 * A document's file on its fiche: sent, replaced, downloaded, previewed (images, PDF) and removed in
 * place. It does not go through the cell-edit overlay: the file travels by its own endpoints, not as
 * an answer, and the API only takes the bearer token, so the bytes are fetched with it and handed to
 * the browser as an object URL.
 */
export function FileCell({ documentId, file, editable, onChanged }: FileCellProps) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [preview, setPreview] = useState<{ url: string; kind: "image" | "pdf" } | null>(null);
  // The preview shown in the form itself, loaded as soon as the file is known to be an image or a PDF.
  const [inline, setInline] = useState<string | null>(null);
  // Bumped by an upload: a replacement may keep the name, type and size the preview was keyed on.
  const [version, setVersion] = useState(0);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(messageForError(e, t("file.failed")));
    } finally {
      setBusy(false);
    }
  }

  const upload = (selected: File | undefined) =>
    selected &&
    run(async () => {
      await uploadDocumentFile(documentId, selected);
      setVersion((v) => v + 1);
      onChanged();
    });

  const download = () =>
    run(async () => {
      const url = URL.createObjectURL(await fetchDocumentFile(documentId, true));
      const link = document.createElement("a");
      link.href = url;
      link.download = file?.fileName ?? "document";
      link.click();
      URL.revokeObjectURL(url);
    });

  const kind = previewKind(file?.mimeType);
  const canInline = kind != null && file?.fileName != null && (file.size ?? 0) <= INLINE_PREVIEW_MAX_BYTES;

  useEffect(() => {
    if (!canInline) {
      setInline(null);
      return;
    }
    let url: string | null = null;
    let cancelled = false;
    fetchDocumentFile(documentId)
      .then((blob) => {
        if (cancelled) return;
        url = URL.createObjectURL(blob);
        setInline(url);
      })
      .catch(() => {
        // The preview is a convenience: without it the file is still there to download.
        if (!cancelled) setInline(null);
      });
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [canInline, documentId, file?.fileName, file?.mimeType, file?.size, version]);
  const openPreview = () =>
    run(async () => {
      if (kind) setPreview({ url: URL.createObjectURL(await fetchDocumentFile(documentId)), kind });
    });
  const closePreview = () => {
    if (preview) URL.revokeObjectURL(preview.url);
    setPreview(null);
  };

  const remove = () =>
    run(async () => {
      await removeDocumentFile(documentId);
      setConfirming(false);
      onChanged();
    });

  return (
    <span className="file-cell">
      {file?.fileName ? (
        <span className="file-cell-name" title={file.fileName}>
          <i className="bi bi-paperclip" /> {file.fileName}
          {file.size != null && <span className="file-cell-size"> ({formatFileSize(file.size)})</span>}
        </span>
      ) : (
        <span className="field-value-cell-empty">{editable ? "—" : t("file.none")}</span>
      )}

      <span className="file-cell-actions">
        {file?.fileName && (
          <>
            <Button type="button" text size="small" icon="bi bi-download" label={t("file.download")} disabled={busy} onClick={download} />
            {kind && <Button type="button" text size="small" icon="bi bi-eye" label={t("file.preview")} disabled={busy} onClick={openPreview} />}
          </>
        )}
        {editable && (
          <>
            <Button
              type="button"
              text
              size="small"
              icon="bi bi-upload"
              label={file?.fileName ? t("file.replace") : t("file.upload")}
              disabled={busy}
              onClick={() => input.current?.click()}
            />
            <input
              ref={input}
              type="file"
              hidden
              data-testid="file-cell-input"
              onChange={(e) => {
                const selected = e.target.files?.[0];
                e.target.value = "";
                void upload(selected);
              }}
            />
            {file?.fileName &&
              (confirming ? (
                <span className="file-cell-confirm">
                  {t("file.removeConfirm")}{" "}
                  <Button type="button" text size="small" severity="danger" label={t("file.removeYes")} disabled={busy} onClick={remove} />
                  <Button type="button" text size="small" label={t("common.cancel")} onClick={() => setConfirming(false)} />
                </span>
              ) : (
                <Button type="button" text size="small" severity="danger" icon="bi bi-trash" label={t("file.remove")} disabled={busy} onClick={() => setConfirming(true)} />
              ))}
          </>
        )}
      </span>
      {error && <small className="p-error file-cell-error">{error}</small>}

      {inline && kind === "image" && (
        <button type="button" className="file-cell-inline file-cell-inline-image" title={t("file.preview")} onClick={openPreview}>
          <img src={inline} alt={file?.fileName ?? ""} />
        </button>
      )}
      {inline && kind === "pdf" && (
        <iframe className="file-cell-inline file-cell-inline-pdf" src={inline} title={file?.fileName ?? ""} />
      )}

      <Dialog
        visible={preview != null}
        onHide={closePreview}
        header={file?.fileName ?? t("file.preview")}
        style={{ width: "min(90vw, 60rem)" }}
        dismissableMask
      >
        {preview?.kind === "image" && <img src={preview.url} alt={file?.fileName ?? ""} style={{ maxWidth: "100%" }} />}
        {preview?.kind === "pdf" && <iframe src={preview.url} title={file?.fileName ?? ""} style={{ width: "100%", height: "70vh", border: 0 }} />}
      </Dialog>
    </span>
  );
}
