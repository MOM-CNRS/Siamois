package fr.siamois.ui.api.openapi.v1.generic.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Meta {
    @Schema(
            description = "Optional: included only if requested via `counts` query parameter",
            required = false
    )
    private Long count;
}

