package fr.siamois.ui.api.openapi.v1.response.vocabulary;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class VocabulariesResponse extends Response<VocabulariesData> {

    public VocabulariesResponse(VocabulariesData data) {
        super(data);
    }
}
