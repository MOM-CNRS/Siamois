package fr.siamois.ui.api.openapi.v1.auth.dto;

import java.util.List;

public record AuthUserResponse(
        Long id,
        String email,
        String username,
        String name,
        String lastname,
        List<OrganizationSummaryResponse> organizations
) {}
