package fr.siamois.dto.entity;

/** What the form's file field shows of a document's stored file. */
public record DocumentFileDTO(String fileName, String mimeType, Long size) {
}
