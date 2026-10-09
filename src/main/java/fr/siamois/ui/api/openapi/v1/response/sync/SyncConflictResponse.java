package fr.siamois.ui.api.openapi.v1.response.sync;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** The 409 of a stale revision: the standard error keys, plus the server state in {@code data}. */
@Getter
@EqualsAndHashCode(callSuper = false)
@Schema(description = "Conflit de révision : erreur standard (error=conflict) et état serveur dans data")
public class SyncConflictResponse extends Response<SyncConflictData> {

    @Schema(description = "Toujours conflict", example = "conflict")
    private final String error = "conflict";

    @Schema(description = "Explication lisible, non contractuelle")
    private final String message = "La ressource a été modifiée depuis la révision lue";

    public SyncConflictResponse(SyncConflictData data) {
        super(data);
    }
}
