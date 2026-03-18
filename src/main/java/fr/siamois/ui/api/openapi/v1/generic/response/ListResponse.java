package fr.siamois.ui.api.openapi.v1.generic.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ListResponse<T>  extends Response<List<T>> {

    private ListMeta meta;

    public ListResponse(List<T> data, ListMeta meta) {
        super(data);
        this.meta = meta;
    }
}