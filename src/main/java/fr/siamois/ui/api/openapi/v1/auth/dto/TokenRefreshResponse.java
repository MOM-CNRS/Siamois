package fr.siamois.ui.api.openapi.v1.auth.dto;

public record TokenRefreshResponse(String accessToken, long expiresIn, String tokenType) {}
