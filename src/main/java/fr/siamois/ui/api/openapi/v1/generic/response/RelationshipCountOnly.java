package fr.siamois.ui.api.openapi.v1.generic.response;


import lombok.Data;

@Data
public class RelationshipCountOnly  {
    private Meta meta;
    public RelationshipCountOnly(Long count) {
        meta = new Meta(count);
    }
}
