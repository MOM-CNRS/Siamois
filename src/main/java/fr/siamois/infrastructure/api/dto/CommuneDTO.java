package fr.siamois.infrastructure.api.dto;


public record CommuneDTO(
        Long id,
        String codeInsee,
        String nom
) {}

