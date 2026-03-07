package fr.siamois.ui.api.openapi.v1.generic;


import lombok.Data;

import java.util.List;

@Data
public class RelationshipToMany<T> extends Relationship<List<T>> {

    public RelationshipToMany(List<T> data) {
        super(data);
    }
}
