package fr.siamois.ui.api.openapi.v1.response.vocabulary;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ProjectFieldCodesResponse extends Response<List<String>> {

    public ProjectFieldCodesResponse(List<String> data) {
        super(data);
    }
}
