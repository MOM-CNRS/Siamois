package fr.siamois.ui.api.openapi.v1.resource.type;

import lombok.Data;

@Data
public class RecordingUnitIdentifierConfig {

    private String identifierFormat;
    private int minCode;
    private int maxCode;

}
