package fr.siamois.infrastructure.api.dto;

import java.util.List;

public record MatiereDTO(
        Long id,
        String valeur,
        String code,
        List<Long> parents
) {}