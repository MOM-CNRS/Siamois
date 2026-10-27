package fr.siamois.infrastructure.api.dto;

import lombok.Data;

@Data
public class CandidateDTO {
    private ValueLangDTO[] terme;
    private String thesoId;
    private String collectionId;
    private String conceptGenericId;
    private ValueLangDTO[] definition;
    private ValueLangDTO[] note;
    private String source;
    private ValueLangDTO[] synonymes;
    private String comment;
}
