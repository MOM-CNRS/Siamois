package fr.siamois.domain.models.vocabulary;

import fr.siamois.infrastructure.api.dto.FullInfoDTO;

import java.util.List;

public record FeedbackFieldConfig(List<String> missingFieldCode, List<FullInfoDTO> conceptWithValidFieldCode) {

    public boolean isWrongConfig() {
        return !missingFieldCode.isEmpty();
    }

    public boolean isValid() {
        return missingFieldCode.isEmpty();
    }
}
