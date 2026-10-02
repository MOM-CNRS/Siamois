package fr.siamois.dto.entity;

import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.time.Instant;

/** Ligne de la liste des modèles d'export d'une institution. */
public record ExportTemplateDTO(
        Long id,
        String templateUuid,
        String name,
        String version,
        @Nullable Long referenceProjectId,
        Instant createdAt,
        Instant updatedAt) implements Serializable {
}
