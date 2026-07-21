package fr.siamois.ui.api.openapi.v1.response.vocabulary;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class VocabularyListResponse extends ListResponse<VocabularyResource> {

    public VocabularyListResponse(List<VocabularyResource> data, ListMeta meta) {
        super(data, meta);
    }
}
