package fr.siamois.ui.api.openapi.v1.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

public record OrganizationSummaryResponse(@Schema(type = "string", example = "100") @JsonSerialize(using = ToStringSerializer.class) Long id, String name) {}
