package fr.siamois.ui.api.openapi.v1.response.form;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldValuesResponse extends ListResponse<ResourceRef> {
    public FieldValuesResponse(List<ResourceRef> data, ListMeta meta) {
        super(data, meta);
    }
}
