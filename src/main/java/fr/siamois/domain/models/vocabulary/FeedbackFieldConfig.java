package fr.siamois.domain.models.vocabulary;

import fr.siamois.infrastructure.api.dto.FullInfoDTO;

import java.util.List;

public class FeedbackFieldConfig {

    private final List<String> missingFieldCode;
    private final List<FullInfoDTO> conceptWithValidFieldCode;

    public FeedbackFieldConfig(List<String> missingFieldCode, List<FullInfoDTO> conceptWithValidFieldCode) {
        this.missingFieldCode = missingFieldCode;
        this.conceptWithValidFieldCode = conceptWithValidFieldCode;
    }

    public List<String> missingFieldCode() {
        return missingFieldCode;
    }

    public List<FullInfoDTO> conceptWithValidFieldCode() {
        return conceptWithValidFieldCode;
    }

    public boolean isWrongConfig() {
        return !missingFieldCode.isEmpty();
    }

    public boolean isValid() {
        return missingFieldCode.isEmpty();
    }
}
