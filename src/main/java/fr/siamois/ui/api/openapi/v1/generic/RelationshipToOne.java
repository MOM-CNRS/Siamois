package fr.siamois.ui.api.openapi.v1.generic;


import lombok.Data;

@Data
public class RelationshipToOne<T> extends Relationship<T> {

    public RelationshipToOne(T data) {
        super(data);
    }
}
