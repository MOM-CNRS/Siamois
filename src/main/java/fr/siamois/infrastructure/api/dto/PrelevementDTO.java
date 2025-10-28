package fr.siamois.infrastructure.api.dto;

import java.util.List;

public record PrelevementDTO(
        Long id,
        Long id_matiere,
        Long id_matiere_type,
        String determination,
        List<Long> commune_ids,
        Long commune_principale_id,
        List<PhotographieDTO> photographies,
        List<CartelDTO> cartels,
        List<MediaDTO> medias
) {}

