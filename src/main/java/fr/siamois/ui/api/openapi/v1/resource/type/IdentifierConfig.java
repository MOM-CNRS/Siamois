package fr.siamois.ui.api.openapi.v1.resource.type;

import lombok.Data;

@Data
public class IdentifierConfig {

    private String recordingUnitIdentifierFormat;
    private String recordingUnitIdentifierLang;
    private Integer maxRecordingUnitCode;
    private Integer minRecordingUnitCode;

}
