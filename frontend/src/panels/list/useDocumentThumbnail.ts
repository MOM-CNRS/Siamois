import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchDocumentFile, fetchDocumentThumbnail } from "../../entities/document/api";

const THUMBNAIL_WIDTH = 480;

/**
 * An object URL for an image document. The API takes only the bearer token, so an <img src> cannot
 * reach it: the bytes are fetched with the token (react-query keeps them across pages) and handed to
 * the browser as an object URL, revoked when the card goes away. `width` asks for a reduced copy;
 * none: the whole file.
 */
function useDocumentImageUrl(documentId: string | number | undefined, enabled: boolean, width: number | null): string | null {
  const { data: blob } = useQuery({
    queryKey: ["document-image", String(documentId), width],
    queryFn: () =>
      width == null
        ? fetchDocumentFile(documentId as string | number)
        : fetchDocumentThumbnail(documentId as string | number, width),
    enabled: enabled && documentId != null,
    staleTime: Infinity,
    gcTime: 5 * 60 * 1000,
    retry: false,
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blob) {
      setUrl(null);
      return;
    }
    const created = URL.createObjectURL(blob);
    setUrl(created);
    return () => URL.revokeObjectURL(created);
  }, [blob]);
  return url;
}

/** A card's picture: a reduced copy, fetched once the card is near the viewport. */
export function useDocumentThumbnail(documentId: string | number | undefined, enabled: boolean): string | null {
  return useDocumentImageUrl(documentId, enabled, THUMBNAIL_WIDTH);
}

/** The whole image, for the zoom dialog (fetched only while it is open). */
export function useDocumentFullImage(documentId: string | number | undefined, enabled: boolean): string | null {
  return useDocumentImageUrl(documentId, enabled, null);
}
