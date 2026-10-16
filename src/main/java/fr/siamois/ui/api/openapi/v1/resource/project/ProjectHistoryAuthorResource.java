package fr.siamois.ui.api.openapi.v1.resource.project;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Auteur d'une révision")
public record ProjectHistoryAuthorResource(
        @Schema(type = "string", example = "7") @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String lastname
) {
}
