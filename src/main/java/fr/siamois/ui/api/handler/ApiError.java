package fr.siamois.ui.api.handler;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ApiError {

    private int status;
    private String error;
    private String message;
    private String path;
    private OffsetDateTime timestamp;
}
