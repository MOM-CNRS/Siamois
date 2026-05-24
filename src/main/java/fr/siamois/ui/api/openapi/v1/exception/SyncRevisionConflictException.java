package fr.siamois.ui.api.openapi.v1.exception;

import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Conflit de révision : le client a travaillé sur une version obsolète de l'entité.
 */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class SyncRevisionConflictException extends RuntimeException {

    private final SyncConflictData conflictData;

    public SyncRevisionConflictException(SyncConflictData conflictData) {
        super("Conflit de synchronisation : l'entité a été modifiée sur le serveur.");
        this.conflictData = conflictData;
    }
}
