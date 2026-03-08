package fr.siamois.ui.api.openapi.v1.generic.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ListMeta {

    private Long total;
    private Integer limit;
    private Long offset;

}

