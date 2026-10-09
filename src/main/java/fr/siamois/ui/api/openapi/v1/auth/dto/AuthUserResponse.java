package fr.siamois.ui.api.openapi.v1.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AuthUserResponse(
        @Schema(type = "string", example = "7") @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String name,
        String lastname,
        List<OrganizationSummaryResponse> organizations
) {}
