package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse de {@code GET /api/v1/recording-units/{id}/history}. Miroir des labels "dernière mise à jour
 * par / le" et "contributeurs" du header JSF ({@code HistoryAuditService.findLastRevisionInfoFor}/
 * {@code findAllContributorsFor}). Ne couvre pas le diff/la restauration d'une révision : cette
 * fonctionnalité n'est pas implémentée côté application non plus aujourd'hui.
 */
public record RecordingUnitHistoryResponse(
        @Schema(description = "Date de la dernière révision, si connue.")
        @Nullable OffsetDateTime lastRevisionAt,
        @Schema(description = "Auteur de la dernière révision, si connu.")
        @Nullable PersonResource lastRevisionBy,
        @Schema(description = "Contributeurs distincts sur l'ensemble des révisions.")
        List<PersonResource> contributors
) {
}
