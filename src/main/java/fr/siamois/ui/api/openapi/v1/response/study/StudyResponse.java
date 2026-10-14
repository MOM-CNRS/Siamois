package fr.siamois.ui.api.openapi.v1.response.study;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.study.StudyResource;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class StudyResponse extends Response<StudyResource> {
    public StudyResponse(StudyResource data) {
        super(data);
    }
}
