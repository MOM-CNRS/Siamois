package fr.siamois.infrastructure.api.dto;

public record CartelDTO(
        Long id,
        String cartel_vitrine,
        Long id_chronologie_vitrine
) {}
